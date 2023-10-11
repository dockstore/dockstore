package io.dockstore.webservice.jdbi;

import com.google.common.base.MoreObjects;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Event.Builder;
import io.dockstore.webservice.core.Event.EventType;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dropwizard.hibernate.AbstractDAO;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDAO extends AbstractDAO<Event> {
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 10;
    public static final String PAGINATION_RANGE = "range[1,100]";
    private static final Logger LOG = LoggerFactory.getLogger(EventDAO.class);

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
        return findEvents((cb, event) -> organizationPredicate(cb, event, Set.of(organizationId)), offset, limit, null, null);
    }

    public List<Event> findEventsForInitiatorUser(User loggedInUser, long initiatorUserId, Integer offset, Integer limit) {
        return findEvents((cb, event) -> initiatorPredicate(cb, event, initiatorUserId), offset, limit, loggedInUser, initiatorUserId);
    }

    public long countAllEventsForOrganization(long organizationId) {
        final Query<?> query = namedQuery("io.dockstore.webservice.core.Event.countAllForOrganization")
                .setParameter("organizationId", organizationId);
        return (Long) query.getSingleResult();
    }

    public List<Event> findEventsByEntryIDs(User loggedInUser, Set<Long> entryIds, Integer offset, int limit) {
        return findEvents((cb, event) -> entryPredicate(cb, event, entryIds), offset, limit, loggedInUser, null);
    }

    public List<Event> findAllByOrganizationIds(User loggedInUser, Set<Long> organizationIds, Integer offset, int limit) {
        return findEvents((cb, event) -> organizationPredicate(cb, event, organizationIds), offset, limit, loggedInUser, null);
    }

    public List<Event> findAllByOrganizationIdsOrEntryIds(User loggedInUser, Set<Long> organizationIds, Set<Long> entryIds, Integer offset, int limit) {
        return findEvents((cb, event) -> cb.or(organizationPredicate(cb, event, organizationIds), entryPredicate(cb, event, entryIds)),
            offset, limit, loggedInUser, null);
    }

    public List<Event> findEvents(BiFunction<CriteriaBuilder, Root<Event>, Predicate> predicateCalculator, Integer offset, Integer limit, User loggedInUser, Long initiatorUserId) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> event = query.from(Event.class);

        Predicate calculatedPredicate = predicateCalculator.apply(cb, event);
        Predicate categoryPredicate = categoryPredicate(cb, event, loggedInUser, initiatorUserId);

        query.select(event);
        query.where(cb.and(calculatedPredicate, categoryPredicate));
        query.orderBy(cb.desc(event.get("id")));

        int checkedOffset = Math.max(MoreObjects.firstNonNull(offset, 0), 0);
        int checkedLimit = Math.min(MoreObjects.firstNonNull(limit, DEFAULT_LIMIT), MAX_LIMIT);

        return currentSession().createQuery(query).setFirstResult(checkedOffset).setMaxResults(checkedLimit).getResultList();
    }

    private Predicate organizationPredicate(CriteriaBuilder cb, Root<Event> event, Set<Long> organizationIds) {
        return event.get("organization").in(organizationIds);
    }

    private Predicate entryPredicate(CriteriaBuilder cb, Root<Event> event, Set<Long> entryIds) {
        return cb.or(
            event.get("tool").in(entryIds),
            event.get("workflow").in(entryIds),
            event.get("apptool").in(entryIds),
            event.get("service").in(entryIds),
            event.get("notebook").in(entryIds));
    }

    private Predicate initiatorPredicate(CriteriaBuilder cb, Root<Event> event, long initiatorUserId) {
        return event.get("initiatorUser").in(initiatorUserId);
    }

    private Predicate categoryPredicate(CriteriaBuilder cb, Root<Event> event, User loggedInUser, Long initiatorUserId) {
        boolean privileged = loggedInUser != null && (loggedInUser.getIsAdmin() || loggedInUser.isCurator());
        boolean sameUser = initiatorUserId != null && loggedInUser != null && loggedInUser.getId() == initiatorUserId;
        if (privileged || sameUser) {
            return cb.conjunction(); // always true
        } else {
            Join<Entry, Organization> join = event.join("organization", JoinType.LEFT);
            return cb.or(cb.isFalse(join.get("categorizer")), cb.isNull(join));
        }
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
        createEntryEvent(publish ? EventType.PUBLISH_ENTRY : EventType.UNPUBLISH_ENTRY, user, entry);
    }

    public <T extends Entry> void archiveEvent(boolean archive, User user, T entry) {
        createEntryEvent(archive ? EventType.ARCHIVE_ENTRY : EventType.UNARCHIVE_ENTRY, user, entry);
    }

    private <T extends Entry> void createEntryEvent(EventType type, User user, T entry) {
        final Builder builder = entry.getEventBuilder()
            .withType(type)
            .withUser(user).withInitiatorUser(user);
        final Event event = builder.build();
        create(event);
    }
}
