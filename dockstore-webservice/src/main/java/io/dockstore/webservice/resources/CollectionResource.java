package io.dockstore.webservice.resources;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Organisation;
import io.dockstore.webservice.core.OrganisationUser;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.CollectionDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.OrganisationDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * Collection of collection endpoints
 * @author aduncan
 */
@Path("/organisations")
@Api("/organisations")
@Produces(MediaType.APPLICATION_JSON)
public class CollectionResource implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(OrganisationResource.class);

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for approved organisations, authentication can be provided for unapproved organisations";

    private final CollectionDAO collectionDAO;
    private final OrganisationDAO organisationDAO;
    private final WorkflowDAO workflowDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;

    public CollectionResource(SessionFactory sessionFactory) {
        this.collectionDAO = new CollectionDAO(sessionFactory);
        this.organisationDAO = new OrganisationDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("{organisationId}/collections/{collectionId}")
    @ApiOperation(value = "Retrieves a collection by ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection getCollectionById(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId) {

        if (!user.isPresent()) {
            // No user given, only show collections from approved organisations
            Collection collection = collectionDAO.findById(collectionId);
            if (collection == null) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
            Organisation organisation = organisationDAO.findApprovedById(collection.getOrganisation().getId());
            if (organisation == null) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }

            return collection;
        } else {
            // User is given, check if the collections organisation is either approved or the user has access
            // Admins and curators should be able to see collections from unapproved organisations
            boolean doesCollectionExist = doesCollectionExistToUser(collectionId, user.get().getId()) || user.get().getIsAdmin() || user.get().isCurator();
            if (!doesCollectionExist) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }

            Collection collection = collectionDAO.findById(collectionId);
            Hibernate.initialize(collection.getEntries());
            return collection;
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("{organisationId}/collections/{collectionId}/entry")
    @ApiOperation(value = "Add an entry to a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection addEntryToCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Entry ID", required = true) @QueryParam("entryId") Long entryId) {
        // Call common code to check if entry and collection exist and return them
        ImmutablePair<Entry, Collection> entryAndCollection = commonModifyCollection(entryId, collectionId, user);

        // Add the entry to the collection
        entryAndCollection.getRight().addEntry(entryAndCollection.getLeft());

        // Event for addition
        Organisation organisation = organisationDAO.findById(organisationId);

        Event.Builder eventBuild = new Event.Builder()
                .withOrganisation(organisation)
                .withCollection(entryAndCollection.getRight())
                .withInitiatorUser(user)
                .withType(Event.EventType.ADD_TO_COLLECTION);

        if (entryAndCollection.getLeft() instanceof Workflow) {
            eventBuild = eventBuild.withWorkflow((Workflow)entryAndCollection.getLeft());
        } else {
            eventBuild = eventBuild.withTool((Tool)entryAndCollection.getLeft());
        }

        Event addToCollectionEvent = eventBuild.build();
        eventDAO.create(addToCollectionEvent);

        return collectionDAO.findById(collectionId);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("{organisationId}/collections/{collectionId}/entry")
    @ApiOperation(value = "Delete an entry from a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection deleteEntryFromCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Entry ID", required = true) @QueryParam("entryId") Long entryId) {
        // Call common code to check if entry and collection exist and return them
        ImmutablePair<Entry, Collection> entryAndCollection = commonModifyCollection(entryId, collectionId, user);

        // Remove the entry from the organisation
        entryAndCollection.getRight().removeEntry(entryAndCollection.getLeft());

        // Event for deletion
        Organisation organisation = organisationDAO.findById(organisationId);

        Event.Builder eventBuild = new Event.Builder()
                .withOrganisation(organisation)
                .withCollection(entryAndCollection.getRight())
                .withInitiatorUser(user)
                .withType(Event.EventType.REMOVE_FROM_COLLECTION);

        if (entryAndCollection.getLeft() instanceof Workflow) {
            eventBuild = eventBuild.withWorkflow((Workflow)entryAndCollection.getLeft());
        } else {
            eventBuild = eventBuild.withTool((Tool)entryAndCollection.getLeft());
        }

        Event removeFromCollectionEvent = eventBuild.build();
        eventDAO.create(removeFromCollectionEvent);

        return collectionDAO.findById(collectionId);
    }

    /**
     * Common code used to add and delete from a collection. Will ensure that both the entry and collection are
     * visible to the user and that the user has the correct credentials to edit the collection.
     * @param entryId
     * @param collectionId
     * @param user User performing the action
     * @return Pair of found Entry and Collection
     */
    private ImmutablePair<Entry, Collection> commonModifyCollection(Long entryId, Long collectionId, User user) {
        // Check that entry exists (could use either workflowDAO or toolDAO here)
        // Note that only published entries can be added
        Entry<? extends Entry, ? extends Version> entry = workflowDAO.getGenericEntryById(entryId);
        if (entry == null || !entry.getIsPublished()) {
            String msg = "Entry not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        // Check that collection exists to user
        boolean doesCollectionExist = doesCollectionExistToUser(collectionId, user.getId()) || user.getIsAdmin() || user.isCurator();
        if (!doesCollectionExist) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Collection collection = collectionDAO.findById(collectionId);

        // Check that user is a member of the organisation
        Organisation organisation = organisationDAO.findById(collection.getOrganisation().getId());

        OrganisationUser organisationUser = getUserOrgRole(organisation, user.getId());
        if (organisationUser == null) {
            String msg = "User does not have rights to modify a collection from this organisation.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        return new ImmutablePair<>(entry, collection);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("{organisationId}/collections")
    @ApiOperation(value = "Retrieve all collections for an organisation.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, responseContainer = "List", response = Collection.class)
    public List<Collection> getCollectionsFromOrganisation(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId, @QueryParam("include") String include) {
        if (!user.isPresent()) {
            Organisation organisation = organisationDAO.findApprovedById(organisationId);
            if (organisation == null) {
                String msg = "Organisation not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
        } else {
            boolean doesOrgExist = doesOrganisationExistToUser(organisationId, user.get().getId());
            if (!doesOrgExist) {
                String msg = "Organisation not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
        }

        List<Collection> collections = collectionDAO.findAllByOrg(organisationId);

        if (checkIncludes(include, "entries")) {
            collections.forEach(collection -> Hibernate.initialize(collection.getEntries()));
        }

        return collectionDAO.findAllByOrg(organisationId);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("{organisationId}/collections")
    @ApiOperation(value = "Create a collection in the given organisation.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection createCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "Collection to register.", required = true) Collection collection) {

        // First check if the organisation exists
        boolean doesOrgExist = doesOrganisationExistToUser(organisationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organisation not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        // Check if any other collections in the organisation exist with that name
        Collection matchingCollection = collectionDAO.findByNameAndOrg(collection.getName(), organisationId);
        if (matchingCollection != null) {
            String msg = "A collection already exists with the name '" + collection.getName() + "' in the specified organisation.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Get the organisation
        Organisation organisation = organisationDAO.findById(organisationId);

        // Save the collection
        long id = collectionDAO.create(collection);
        organisation.addCollection(collection);

        // Event for creation
        User foundUser = userDAO.findById(user.getId());
        Event createCollectionEvent = new Event.Builder()
                .withOrganisation(organisation)
                .withCollection(collection)
                .withInitiatorUser(foundUser)
                .withType(Event.EventType.CREATE_COLLECTION)
                .build();
        eventDAO.create(createCollectionEvent);

        return collectionDAO.findById(id);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("{organisationId}/collections/{collectionId}")
    @ApiOperation(value = "Update a collection.", notes = "Currently only name and description can be updated.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection updateCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Collection to update with.", required = true) Collection collection,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long organisationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId) {
        // Ensure collection exists to the user
        boolean doesCollectionExist = doesCollectionExistToUser(collectionId, user.getId());
        if (!doesCollectionExist) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Collection existingCollection = collectionDAO.findById(collectionId);
        Organisation organisation = organisationDAO.findById(existingCollection.getOrganisation().getId());

        // Check that the user has rights to the organisation
        OrganisationUser organisationUser = getUserOrgRole(organisation, user.getId());
        if (organisationUser == null) {
            String msg = "User does not have rights to modify a collection from this organisation.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        // Check if new name is valid
        if (!Objects.equals(existingCollection.getName(), collection.getName())) {
            Collection duplicateName = collectionDAO.findByNameAndOrg(collection.getName(), existingCollection.getOrganisation().getId());
            if (duplicateName != null) {
                String msg = "A collection already exists with the name '" + collection.getName() + "', please try another one.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
        }

        // Update the collection
        existingCollection.setName(collection.getName());
        existingCollection.setDescription(collection.getDescription());

        // Event for update
        Event updateCollectionEvent = new Event.Builder()
                .withOrganisation(organisation)
                .withCollection(collection)
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_COLLECTION)
                .build();
        eventDAO.create(updateCollectionEvent);

        return collectionDAO.findById(collectionId);

    }

    /**
     * For a collection to exist to a user, it must either be from an approved organisation
     * or an organisation the user has access to.
     * @param collectionId
     * @param userId
     * @return True if collection exists to user, false otherwise
     */
    private boolean doesCollectionExistToUser(Long collectionId, Long userId) {
        // A collection is only visible to a user if the organisation it belongs to is approved or they are a member
        Collection collection = collectionDAO.findById(collectionId);
        if (collection == null) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        return doesOrganisationExistToUser(collection.getOrganisation().getId(), userId);
    }

    /**
     * Checks if the given user should know of the existence of the organisation
     * For a user to see an organsation, either it must be approved or the user must have a role in the organisation
     * @param organisationId
     * @param userId
     * @return True if organisation exists to user, false otherwise
     */
    private boolean doesOrganisationExistToUser(Long organisationId, Long userId) {
        Organisation organisation = organisationDAO.findById(organisationId);
        if (organisation == null) {
            return false;
        }
        OrganisationUser organisationUser = getUserOrgRole(organisation, userId);
        return organisation.isApproved() || (organisationUser != null);
    }

    /**
     * Determine the role of a user in an organisation
     * @param organisation
     * @param userId
     * @return OrganisationUser role
     */
    private OrganisationUser getUserOrgRole(Organisation organisation, Long userId) {
        Set<OrganisationUser> organisationUserSet = organisation.getUsers();
        Optional<OrganisationUser> matchingUser = organisationUserSet.stream().filter(organisationUser -> Objects
                .equals(organisationUser.getUser().getId(), userId)).findFirst();
        return matchingUser.orElse(null);
    }

    /**
     * Checks if the include string (csv) includes some field
     * @param include CSV string where each field is of the form [a-zA-Z]+
     * @param field Field to query for
     * @return True if include has the given field, false otherwise
     */
    private boolean checkIncludes(String include, String field) {
        String includeString = (include == null ? "" : include);
        List<String> includeSplit = Arrays.asList(includeString.split(","));
        return includeSplit.contains(field);
    }
}
