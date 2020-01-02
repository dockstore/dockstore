package io.dockstore.webservice.jdbi;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.dockstore.webservice.core.Event;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class EventDAO extends AbstractDAO<Event> {
    public static final int MAX_LIMIT = 100;
    public static final String PAGINATION_RANGE = "range[1,100]";
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

    public List<Event> findEventsByEntryIDs(Set<Long> entryIds, Integer offset, int limit) {
        limit = Math.min(MAX_LIMIT, limit);
        if (entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByEntry");
        query.setParameterList("entryIDs", entryIds).setFirstResult(offset).setMaxResults(limit);
        return list(query);
    }

    public List<Event> findEventsByUserId(long userId) {
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByUserId");
        query.setParameter("userId", userId);
        return list(query);
    }

    public List<Event> findEventsByUserId(long userId, Integer offset, int limit) {
        limit = Math.min(MAX_LIMIT, limit);
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByUserId");
        query.setParameter("userId", userId).setFirstResult(offset).setMaxResults(limit);
        return list(query);
    }

    public List<Event> findEventsByEntryId(long entryId) {
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByEntryId");
        query.setParameter("entryId", entryId);
        return list(query);
    }

    public List<Event> findEventsByEntryId(long entryId, Integer offset, Integer limit) {
        Query<Event> query = namedQuery("io.dockstore.webservice.core.Event.findAllByEntryId");
        query.setParameter("entryId", entryId).setFirstResult(offset).setMaxResults(limit);
        return list(query);
    }

    public void delete(Event event) {
        Session session = currentSession();
        session.delete(event);
        session.flush();
    }
}
