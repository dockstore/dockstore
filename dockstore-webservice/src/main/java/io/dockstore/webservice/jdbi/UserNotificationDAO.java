package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.UserNotification;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

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
        Query<UserNotification> query = namedTypedQuery("io.dockstore.webservice.core.UserNotification.findByUser")
            .setParameter("user", user);
        return list(query);
    }
}
