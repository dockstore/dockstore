package io.dockstore.webservice.jdbi;

import com.google.common.base.MoreObjects;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class LambdaEventDAO extends AbstractDAO<LambdaEvent> {
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

    public List<LambdaEvent> findByUser(User user, String offset, Integer limit) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<LambdaEvent> query = criteriaQuery();
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(event.get("user"), user));
        query.orderBy(cb.desc(event.get("id")));
        query.where(predicates.toArray(new Predicate[]{}));

        query.select(event);

        int primitiveOffset = Integer.parseInt(MoreObjects.firstNonNull(offset, "0"));
        TypedQuery<LambdaEvent> typedQuery = currentSession().createQuery(query).setFirstResult(primitiveOffset).setMaxResults(limit);
        return typedQuery.getResultList();
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
    public List<LambdaEvent> findByOrganization(String organization, String offset, Integer limit, Optional<List<String>> repositories) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<LambdaEvent> query = criteriaQuery();
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(event.get("organization"), organization));
        repositories.ifPresent(repos -> predicates.add(event.get("repository").in(repos)));
        query.orderBy(cb.desc(event.get("id")));
        query.where(predicates.toArray(new Predicate[]{}));

        query.select(event);

        int primitiveOffset = Integer.parseInt(MoreObjects.firstNonNull(offset, "0"));
        TypedQuery<LambdaEvent> typedQuery = currentSession().createQuery(query).setFirstResult(primitiveOffset).setMaxResults(limit);
        return typedQuery.getResultList();
    }
}
