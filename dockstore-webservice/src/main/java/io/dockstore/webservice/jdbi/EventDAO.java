package io.dockstore.webservice.jdbi;

import java.util.List;

import io.dockstore.webservice.core.Event;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class EventDAO extends AbstractDAO<Event> {
    public EventDAO(SessionFactory factory) {
        super(factory);
    }

    public Event findById(Long id) {
        return get(id);
    }

    public long create(Event event) {
        return persist(event).getId();
    }

    public long update(Event event) {
        return persist(event).getId();
    }

    public List<Event> findEventsForOrganization(long organizationId, Integer offset, Integer limit) {
        Query query = namedQuery("io.dockstore.webservice.core.Event.findAllForOrganization")
                .setParameter("organizationId", organizationId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        return list(query);
    }

    public long countAllEventsForOrganization(long organizationId) {
        final Query query = namedQuery("io.dockstore.webservice.core.Event.countAllForOrganization")
                .setParameter("organizationId", organizationId);
        return ((Long)query.getSingleResult()).longValue();
    }

    public void delete(Event event) {
        Session session = currentSession();
        session.delete(event);
        session.flush();
    }
}
