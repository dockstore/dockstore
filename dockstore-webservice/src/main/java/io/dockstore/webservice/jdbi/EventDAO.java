package io.dockstore.webservice.jdbi;

import com.google.common.base.MoreObjects;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Event.Builder;
import io.dockstore.webservice.core.Event.EventType;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
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
import java.util.Optional;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

public class EventDAO extends AbstractDAO<Event> {
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 10;
    public static final String PAGINATION_RANGE = "range[1,100]";
    private static final String IS_PUBLISHED = "isPublished";

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
        return findEvents(null, organizationPredicateBuilder(Set.of(organizationId)), offset, limit);
    }

    public long countAllEventsForOrganization(long organizationId) {
        final Query<?> query = namedQuery("io.dockstore.webservice.core.Event.countAllForOrganization")
                .setParameter("organizationId", organizationId);
        return (Long) query.getSingleResult();
    }

    public List<Event> findEventsByEntryIDs(User loggedInUser, Set<Long> entryIds, Integer offset, int limit) {
        return findEvents(loggedInUser, entryPredicateBuilder(entryIds), offset, limit);
    }

    public List<Event> findAllByOrganizationIds(User loggedInUser, Set<Long> organizationIds, Integer offset, int limit) {
        return findEvents(loggedInUser, organizationPredicateBuilder(organizationIds), offset, limit);
    }

    public List<Event> findAllByOrganizationIdsOrEntryIds(User loggedInUser, Set<Long> organizationIds, Set<Long> entryIds, Integer offset, int limit) {
        return findEvents(loggedInUser, orPredicateBuilder(organizationPredicateBuilder(organizationIds), entryPredicateBuilder(entryIds)), offset, limit);
    }

    public List<Event> findEventsForInitiatorUser(User loggedInUser, long initiatorUserId, Integer offset, Integer limit) {
        return findEvents(loggedInUser, initiatorPredicateBuilder(initiatorUserId), offset, limit);
    }

    private List<Event> findEvents(User loggedInUser, PredicateBuilder predicateBuilder, Integer offset, Integer limit) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> event = query.from(Event.class);

        Predicate specifiedPredicate = predicateBuilder.build(cb, event);
        Predicate accessPredicate = accessPredicateBuilder(loggedInUser).build(cb, event);

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

    private PredicateBuilder accessPredicateBuilder(User loggedInUser) {
        return (cb, event) -> {
            if (loggedInUser != null && (loggedInUser.getIsAdmin() || loggedInUser.isCurator())) {
                // An admin or curator is able to access everything.
                return cb.conjunction();
            }
            // Calculate a predicate representing public access, which, currently, is all events that:
            // 1) Either:
            // a) Don't refer to an entry
            // b) Refer to a published entry, or
            // c) Are of type PUBLISH_ENTRY or UNPUBLISH_ENTRY or COLLECTION-related events
            // and
            // 2) Don't refer to a categorizer organization.
            Join<Event, Tool> tool = event.join("tool", JoinType.LEFT);
            Join<Event, BioWorkflow> workflow = event.join("workflow", JoinType.LEFT);
            Join<Event, AppTool> apptool = event.join("apptool", JoinType.LEFT);
            Join<Event, Service> service = event.join("service", JoinType.LEFT);
            Join<Event, Notebook> notebook = event.join("notebook", JoinType.LEFT);
            Predicate noEntryPredicate = cb.and(
                cb.isNull(tool),
                cb.isNull(workflow),
                cb.isNull(apptool),
                cb.isNull(service),
                cb.isNull(notebook)
            );
            Predicate publishedEntryPredicate = cb.or(
                cb.isTrue(tool.get(IS_PUBLISHED)),
                cb.isTrue(workflow.get(IS_PUBLISHED)),
                cb.isTrue(apptool.get(IS_PUBLISHED)),
                cb.isTrue(service.get(IS_PUBLISHED)),
                cb.isTrue(notebook.get(IS_PUBLISHED))
            );
            Predicate typesPredicate = cb.or(event.get("type").in(Set.of(EventType.PUBLISH_ENTRY, EventType.UNPUBLISH_ENTRY, EventType.CREATE_COLLECTION, EventType.MODIFY_COLLECTION, EventType.DELETE_COLLECTION, EventType.ADD_TO_COLLECTION, EventType.REMOVE_FROM_COLLECTION)));
            Join<Event, Organization> organization = event.join("organization", JoinType.LEFT);
            Predicate nonCategorizerPredicate = cb.or(cb.isFalse(organization.get("categorizer")), cb.isNull(organization));
            Predicate publicPredicate = cb.and(cb.or(noEntryPredicate, publishedEntryPredicate, typesPredicate), nonCategorizerPredicate);

            if (loggedInUser != null) {
                // The logged-in user can also access events that they initiated or were the subject of.
                return cb.or(
                    event.get("initiatorUser").in(loggedInUser.getId()),
                    event.get("user").in(loggedInUser.getId()),
                    publicPredicate
                );
            } else {
                // Public access.
                return publicPredicate;
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

    public void createAddTagToEntryEvent(Optional<User> user, Entry entry, Version version) {
        if (version.getReferenceType() == Version.ReferenceType.TAG) {
            final Builder builder = entry.getEventBuilder().withType(EventType.ADD_VERSION_TO_ENTRY).withVersion(version);
            user.ifPresent(builder::withInitiatorUser);
            final Event event = builder.build();
            create(event);
        }
    }

    public <T extends Entry> void publishEvent(boolean publish, Optional<User> user, T entry) {
        createEntryEvent(publish ? EventType.PUBLISH_ENTRY : EventType.UNPUBLISH_ENTRY, user, entry);
    }

    public <T extends Entry> void archiveEvent(boolean archive, Optional<User> user, T entry) {
        createEntryEvent(archive ? EventType.ARCHIVE_ENTRY : EventType.UNARCHIVE_ENTRY, user, entry);
    }

    private <T extends Entry> void createEntryEvent(EventType type, Optional<User> user, T entry) {
        final Builder builder = entry.getEventBuilder()
            .withType(type);
        user.ifPresent(u -> builder.withInitiatorUser(u).withUser(u));
        final Event event = builder.build();
        create(event);
    }

    private interface PredicateBuilder {
        Predicate build(CriteriaBuilder cb, Root<Event> event);
    }
}
