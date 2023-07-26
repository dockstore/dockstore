package io.dockstore.webservice.jdbi;

import com.google.common.base.MoreObjects;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Event.Builder;
import io.dockstore.webservice.core.Event.EventType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dropwizard.hibernate.AbstractDAO;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
        Query<Event> query = namedTypedQuery("io.dockstore.webservice.core.Event.findAllForOrganization")
                .setParameter("organizationId", organizationId)
                .setFirstResult(offset)
                .setMaxResults(limit);
        return list(query);
    }

    public List<Event> findEventsForInitiatorUser(User loggedInUser, long initiatorUser, Integer offset, Integer limit) {
        Query<Event> query = namedTypedQuery("io.dockstore.webservice.core.Event.findAllByInitiatorUserId")
            .setParameter("initiatorUser", initiatorUser);

        //when viewing your own profile, show all events
        if (loggedInUser != null && loggedInUser.getId() == initiatorUser) {
            query.setFirstResult(offset).setMaxResults(limit);
            return list(query);
        }
        return offsetAndLimitEvents(filterCategoryEvents(loggedInUser, query.getResultList()), offset, limit);
    }

    public long countAllEventsForOrganization(long organizationId) {
        final Query<?> query = namedQuery("io.dockstore.webservice.core.Event.countAllForOrganization")
                .setParameter("organizationId", organizationId);
        return (Long) query.getSingleResult();
    }

    private List<Event> filterCategoryEvents(User loggedInUser, List<Event> events) {
        if (loggedInUser != null && (loggedInUser.isCurator() || loggedInUser.getIsAdmin())) {
            return events;
        } else {
            List<Event> filteredEvents = new ArrayList<>();
            events.forEach(event -> {
                if (event.getOrganization() == null || !event.getOrganization().isCategorizer()) {
                    filteredEvents.add(event);
                }
            });
            return filteredEvents;
        }
    }

    private List<Event> offsetAndLimitEvents(List<Event> events, Integer offset, Integer limit) {
        return events.subList(offset, Math.min(offset + limit, events.size()));
    }

    public List<Event> findEventsByEntryIDs(User loggedInUser, Set<Long> entryIds, Integer offset, int limit) {
        int newLimit = Math.min(MAX_LIMIT, limit);
        if (entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query<Event> query = namedTypedQuery("io.dockstore.webservice.core.Event.findAllByEntryIds");
        query.setParameterList("entryIDs", entryIds);
        return offsetAndLimitEvents(filterCategoryEvents(loggedInUser, query.getResultList()), offset, limit);
    }

    public List<Event> findAllByOrganizationIds(User loggedInUser, Set<Long> organizationIds, Integer offset, int limit) {
        int newLimit = Math.min(MAX_LIMIT, limit);
        if (organizationIds.isEmpty()) {
            return Collections.emptyList();
        }
        Query<Event> query = namedTypedQuery("io.dockstore.webservice.core.Event.findAllByOrganizationIds");
        query.setParameterList("organizationIDs", organizationIds);
        return offsetAndLimitEvents(filterCategoryEvents(loggedInUser, query.getResultList()), offset, limit);
    }

    public List<Event> findAllByOrganizationIdsOrEntryIds(User loggedInUser, Set<Long> organizationIds, Set<Long> entryIds, Integer offset, int limit) {
        int newLimit = Math.min(MAX_LIMIT, limit);

        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Event> query = criteriaQuery();
        Root<Event> event = query.from(Event.class);

        if (organizationIds.isEmpty() && entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Predicate> list = new ArrayList<>();
        if (!organizationIds.isEmpty()) {
            list.add(event.get("organization").in(organizationIds));
        }
        if (!entryIds.isEmpty()) {
            list.add(event.get("tool").in(entryIds));
            list.add(event.get("workflow").in(entryIds));
            list.add(event.get("apptool").in(entryIds));
            list.add(event.get("service").in(entryIds));
            list.add(event.get("notebook").in(entryIds));
        }
        query.where(cb.or(list.toArray(new Predicate[0])));
        query.orderBy(cb.desc(event.get("id")));
        query.select(event);

        int primitiveOffset = MoreObjects.firstNonNull(offset, 0);
        TypedQuery<Event> typedQuery = currentSession().createQuery(query);
        return offsetAndLimitEvents(filterCategoryEvents(loggedInUser, typedQuery.getResultList()), primitiveOffset, newLimit);
    }

    public void delete(Event event) {
        Session session = currentSession();
        session.remove(event);
        session.flush();
    }

    public void deleteEventByEntryID(long entryId) {
        currentSession().flush();
        Query<Event> query = this.currentSession().createNamedQuery("io.dockstore.webservice.core.Event.deleteByEntryId");
        query.setParameter("entryId", entryId);
        query.executeUpdate();
        currentSession().flush();
    }

    public void deleteEventByOrganizationID(long organizationId) {
        Query query = namedQuery("io.dockstore.webservice.core.Event.deleteByOrganizationId");
        query.setParameter("organizationId", organizationId);
        query.executeUpdate();
        // Flush after executing the DELETE query. This would force Hibernate to synchronize the state of the
        // current session with the database so the session can see that an event has been deleted
        currentSession().flush();
    }

    public void createAddTagToEntryEvent(User user, Entry entry, Version version) {
        if (version.getReferenceType() == Version.ReferenceType.TAG) {
            Event event = entry.getEventBuilder().withType(Event.EventType.ADD_VERSION_TO_ENTRY).withInitiatorUser(user).withVersion(version).build();
            create(event);
        }
    }

    public <T extends Entry> void publishEvent(boolean publish, User user, T entry) {
        final Builder builder = entry.getEventBuilder()
            .withType(publish ? EventType.PUBLISH_ENTRY : EventType.UNPUBLISH_ENTRY)
            .withUser(user).withInitiatorUser(user);
        final Event event = builder.build();
        create(event);
    }
}
