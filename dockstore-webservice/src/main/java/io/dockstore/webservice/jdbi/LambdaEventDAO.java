package io.dockstore.webservice.jdbi;

import static io.dockstore.webservice.jdbi.EntryDAO.INVALID_SORTCOL_MESSAGE;

import com.google.common.base.Strings;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LambdaEventDAO extends AbstractDAO<LambdaEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(LambdaEventDAO.class);

    public LambdaEventDAO(SessionFactory factory) {
        super(factory);
    }

    public LambdaEvent findById(Long id) {
        return get(id);
    }

    public long create(LambdaEvent lambdaEvent) {
        return persist(lambdaEvent).getId();
    }

    public long update(LambdaEvent lambdaEvent) {
        return persist(lambdaEvent).getId();
    }

    public void delete(LambdaEvent lambdaEvent) {
        Session session = currentSession();
        session.remove(lambdaEvent);
        session.flush();
    }

    private Predicate createNotNullLikeCriteria(String column, String filter, CriteriaBuilder cb, Root<LambdaEvent> event) {
        return cb.and(cb.isNotNull(event.get(column)), cb.like(cb.upper(event.get(column)), "%" + filter.toUpperCase() + "%"));
    }

    private List<Predicate> processQuery(String filter, String sortCol, String sortOrder, CriteriaBuilder cb, CriteriaQuery query, Root<LambdaEvent> event) {
        List<Predicate> predicates = new ArrayList<>();
        if (!Strings.isNullOrEmpty(filter)) {
            predicates.add(
                // ensure we deal with null values and then do like queries on those non-null values
                cb.or(
                    createNotNullLikeCriteria("message", filter, cb, event),
                    createNotNullLikeCriteria("githubUsername", filter, cb, event),
                    createNotNullLikeCriteria("repository", filter, cb, event),
                    createNotNullLikeCriteria("type", filter, cb, event),
                    createNotNullLikeCriteria("entryName", filter, cb, event),
                    createNotNullLikeCriteria("reference", filter, cb, event),
                    createNotNullLikeCriteria("deliveryId", filter, cb, event)
                )
            );
        }

        if (!Strings.isNullOrEmpty(sortCol)) {
            boolean hasSortCol = event.getModel()
                    .getAttributes()
                    .stream()
                    .map(Attribute::getName)
                    .anyMatch(sortCol::equals);

            if (!hasSortCol) {
                LOG.error(INVALID_SORTCOL_MESSAGE);
                throw new CustomWebApplicationException(INVALID_SORTCOL_MESSAGE,
                        HttpStatus.SC_BAD_REQUEST);

            } else {
                Path<Object> sortPath = event.get(sortCol);
                if ("asc".equalsIgnoreCase(sortOrder)) {
                    query.orderBy(cb.asc(sortPath), cb.desc(event.get("id")));
                } else {
                    query.orderBy(cb.desc(sortPath), cb.desc(event.get("id")));
                }
            }
        }
        return predicates;
    }

    public List<LambdaEvent> findByRepository(String repository) {
        Query<LambdaEvent> query = namedTypedQuery("io.dockstore.webservice.core.LambdaEvent.findByRepository")
                .setParameter("repository", repository);
        return list(query);
    }

    public List<LambdaEvent> findByUsername(String username) {
        Query<LambdaEvent> query = namedTypedQuery("io.dockstore.webservice.core.LambdaEvent.findByUsername")
                .setParameter("username", username);
        return list(query);
    }

    public List<LambdaEvent> findByUser(User user) {
        Query<LambdaEvent> query = namedTypedQuery("io.dockstore.webservice.core.LambdaEvent.findByUser")
                .setParameter("user", user);
        return list(query);
    }

    public List<LambdaEvent> findByUser(User user, Integer offset, Integer limit, String filter, String sortCol, String sortOrder) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<LambdaEvent> query = criteriaQuery();
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> initialPredicates = processQuery(filter, sortCol, sortOrder, cb, query, event);
        setupFindByUserQuery(user, cb, query, initialPredicates, event);

        query.select(event);

        TypedQuery<LambdaEvent> typedQuery = currentSession().createQuery(query).setFirstResult(offset).setMaxResults(limit);
        return typedQuery.getResultList();
    }

    /**
     * Count lambda events filtered by a user.
     * @param user filter for lambda events
     * @return count of lambda events
     */
    public long countByUser(User user, String filter) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> initialPredicate = processQuery(filter, "", "", cb, query, event);
        setupFindByUserQuery(user, cb, query, initialPredicate, event);
        query.select(cb.count(event));

        return currentSession().createQuery(query).getSingleResult();
    }

    private void setupFindByUserQuery(User user, CriteriaBuilder cb, CriteriaQuery<?> query, List<Predicate> predicates, Root<?> event) {
        predicates.add(cb.equal(event.get("user"), user));
        query.where(predicates.toArray(new Predicate[]{}));
    }

    /**
     * Returns a list of lambda events for an organization. If <code>repositories</code> is not
     * empty, it further filters down to the specified repos within the organization.
     * @param organization
     * @param offset
     * @param limit
     * @param repositories
     * @return
     */
    public List<LambdaEvent> findByOrganization(String organization, int offset, int limit, String filter, String sortCol, String sortOrder, Optional<List<String>> repositories) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<LambdaEvent> query = criteriaQuery();
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> initialPredicates = processQuery(filter, sortCol, sortOrder, cb, query, event);
        setupFindByOrganizationQuery(organization, repositories, cb, query, initialPredicates, event);
        query.select(event);

        TypedQuery<LambdaEvent> typedQuery = currentSession().createQuery(query).setFirstResult(offset).setMaxResults(limit);
        return typedQuery.getResultList();
    }

    /**
     * Count lambda events filtered by an organization and a list of repositories.
     * @param organization organization
     * @param repositories optional list of repositories
     * @return count of lambda events
     */
    public long countByOrganization(String organization, Optional<List<String>> repositories, String filter) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> initialPredicate = processQuery(filter, "", "", cb, query, event);
        setupFindByOrganizationQuery(organization, repositories, cb, query, initialPredicate, event);
        query.select(cb.count(event));

        return currentSession().createQuery(query).getSingleResult();
    }

    private void setupFindByOrganizationQuery(String organization, Optional<List<String>> repositories, CriteriaBuilder cb,
                                              CriteriaQuery<?> query, List<Predicate> predicates, Root<?> event) {
        predicates.add(cb.equal(event.get("organization"), organization));
        repositories.ifPresent(repos -> predicates.add(event.get("repository").in(repos)));
        query.where(predicates.toArray(new Predicate[]{}));
    }
}
