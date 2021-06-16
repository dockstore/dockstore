package io.dockstore.webservice.jdbi;

import java.util.List;

import io.dockstore.webservice.core.Notification;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class NotificationDAO extends AbstractDAO<Notification> {
    public NotificationDAO(SessionFactory factory) {
        super(factory);
    }

    public long create(Notification notification) {
        return persist(notification).getId();
    }

    public Notification update(Notification notification) {
        Session session = currentSession();
        session.update(notification);
        session.flush();
        return notification;
    }

    public void delete(Notification notification) {
        Session session = currentSession();
        session.delete(notification);
        session.flush();
    }

    public Notification findById(long id) {
        return get(id);
    }

    public List<Notification> getActiveNotifications() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Notification.getActiveNotifications"));
    }
}
