package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.PublicNotification;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class NotificationDAO extends AbstractDAO<PublicNotification> {
    public NotificationDAO(SessionFactory factory) {
        super(factory);
    }

    public long create(PublicNotification notification) {
        return persist(notification).getId();
    }

    public PublicNotification update(PublicNotification notification) {
        Session session = currentSession();
        session.update(notification);
        session.flush();
        return notification;
    }

    public void delete(PublicNotification notification) {
        Session session = currentSession();
        session.delete(notification);
        session.flush();
    }

    public PublicNotification findById(long id) {
        return get(id);
    }

    public List<PublicNotification> getActiveNotifications() {
        return list(namedTypedQuery("io.dockstore.webservice.core.PublicNotification.getActiveNotifications"));
    }
}
