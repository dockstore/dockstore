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
        return findEvents(organizationPredicateBuilder(Set.of(organizationId)), offset, limit, null, null);
    }

    public long countAllEventsForOrganization(long organizationId) {
        final Query<?> query = namedQuery("io.dockstore.webservice.core.Event.countAllForOrganization")
                .setParameter("organizationId", organizationId);
        return (Long) query.getSingleResult();
    }

    public List<Event> findEventsByEntryIDs(User loggedInUser, Set<Long> entryIds, Integer offset, int limit) {
        return findEvents(entryPredicateBuilder(entryIds), offset, limit, loggedInUser, null);
    }

    public List<Event> findAllByOrganizationIds(User loggedInUser, Set<Long> organizationIds, Integer offset, int limit) {
        return findEvents(organizationPredicateBuilder(organizationIds), offset, limit, loggedInUser, null);
    }

    public List<Event> findAllByOrganizationIdsOrEntryIds(User loggedInUser, Set<Long> organizationIds, Set<Long> entryIds, Integer offset, int limit) {
        return findEvents(orPredicateBuilder(organizationPredicateBuilder(organizationIds), entryPredicateBuilder(entryIds)),
            offset, limit, loggedInUser, null);
    }

    public List<Event> findEventsForInitiatorUser(User loggedInUser, long initiatorUserId, Integer offset, Integer limit) {
        return findEvents(initiatorPredicateBuilder(initiatorUserId), offset, limit, loggedInUser, initiatorUserId);
    }

    private List<Event> findEvents(PredicateBuilder predicateBuilder, Integer offset, Integer limit, User loggedInUser, Long initiatorUserId) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> event = query.from(Event.class);

        Predicate specifiedPredicate = predicateBuilder.build(cb, event);
        Predicate accessPredicate = accessPredicateBuilder(loggedInUser, initiatorUserId).build(cb, event);

        query.select(event);
        query.where(cb.and(specifiedPredicate, accessPredicate));
        query.orderBy(cb.desc(event.get("id")));

        int checkedOffset = Math.max(MoreObjects.firstNonNull(offset, 0), 0);
        int checkedLimit = Math.min(MoreObjects.firstNonNull(limit, DEFAULT_LIMIT), MAX_LIMIT);

        return currentSession().createQuery(query).setFirstResult(checkedOffset).setMaxResults(checkedLimit).getResultList();
    }

    private PredicateBuilder organizationPredicateBuilder(Set<Long> organizationIds) {
        return (cb, event) -> event.get("organization").in(organizationIds);
    }

    private PredicateBuilder entryPredicateBuilder(Set<Long> entryIds) {
        return (cb, event) -> cb.or(
            event.get("tool").in(entryIds),
            event.get("workflow").in(entryIds),
            event.get("apptool").in(entryIds),
            event.get("service").in(entryIds),
            event.get("notebook").in(entryIds));
    }

    private PredicateBuilder orPredicateBuilder(PredicateBuilder a, PredicateBuilder b) {
        return (cb, event) -> cb.or(a.build(cb, event), b.build(cb, event));
    }

    private PredicateBuilder initiatorPredicateBuilder(long initiatorUserId) {
        return (cb, event) -> event.get("initiatorUser").in(initiatorUserId);
    }

    private PredicateBuilder accessPredicateBuilder(User loggedInUser, Long initiatorUserId) {
        return (cb, event) -> {
            boolean privileged = loggedInUser != null && (loggedInUser.getIsAdmin() || loggedInUser.isCurator());
            boolean sameUser = loggedInUser != null && initiatorUserId != null && loggedInUser.getId() == initiatorUserId;
            if (privileged || sameUser) {
                // Always true, full access.
                return cb.conjunction();
            } else {
                // Exclude events relating to categorizer organizations.
                Join<Entry, Organization> organization = event.join("organization", JoinType.LEFT);
                return cb.or(cb.isFalse(organization.get("categorizer")), cb.isNull(organization));
            }
        };
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

    private interface PredicateBuilder {
        Predicate build(CriteriaBuilder cb, Root<Event> event);
    }
}
