package io.dockstore.webservice.jdbi;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import com.google.common.base.MoreObjects;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.User;
import io.dropwizard.hibernate.AbstractDAO;
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
        session.delete(lambdaEvent);
        session.flush();
    }

    public List<LambdaEvent> findByRepository(String repository) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByRepository")
                .setParameter("repository", repository);
        return list(query);
    }

    public List<LambdaEvent> findByUsername(String username) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByUsername")
                .setParameter("username", username);
        return list(query);
    }

    public List<LambdaEvent> findByUser(User user) {
        Query query = namedQuery("io.dockstore.webservice.core.LambdaEvent.findByUser")
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

    public List<LambdaEvent> findByOrganization(String organization, String offset, Integer limit) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<LambdaEvent> query = criteriaQuery();
        Root<LambdaEvent> event = query.from(LambdaEvent.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(event.get("organization"), organization));
        query.orderBy(cb.desc(event.get("id")));
        query.where(predicates.toArray(new Predicate[]{}));

        query.select(event);

        int primitiveOffset = Integer.parseInt(MoreObjects.firstNonNull(offset, "0"));
        TypedQuery<LambdaEvent> typedQuery = currentSession().createQuery(query).setFirstResult(primitiveOffset).setMaxResults(limit);
        return typedQuery.getResultList();
    }
}
