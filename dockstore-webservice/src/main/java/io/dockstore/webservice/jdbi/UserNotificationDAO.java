package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.UserNotification;
import io.dropwizard.hibernate.AbstractDAO;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class UserNotificationDAO extends AbstractDAO<UserNotification> {
    public UserNotificationDAO(SessionFactory factory) {
        super(factory);
    }

    public long create(UserNotification notification) {
        return persist(notification).getId();
    }

    public UserNotification update(UserNotification notification) {
        Session session = currentSession();
        session.update(notification);
        session.flush();
        return notification;
    }

    public void delete(UserNotification notification) {
        Session session = currentSession();
        session.delete(notification);
        session.flush();
    }

    public UserNotification findById(long id) {
        return get(id);
    }

    public List<UserNotification> findByUser(User user) {
        return findByUser(user, 0, Integer.MAX_VALUE);
    }

    public List<UserNotification> findByUser(User user, Integer offset, Integer limit) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<UserNotification> query = cb.createQuery(UserNotification.class);
        Root<UserNotification> userNotificationRoot = query.from(UserNotification.class);
        query.select(userNotificationRoot)
            .where(cb.equal(userNotificationRoot.get("user"), user), cb.isFalse(userNotificationRoot.get("hidden")))
            .orderBy(cb.desc(userNotificationRoot.get("dbCreateDate")));
        return currentSession().createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long getCountByUser(User user) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.UserNotification.getCountByUser", Long.class)
            .setParameter("user", user)
            .getSingleResult();
    }
}
