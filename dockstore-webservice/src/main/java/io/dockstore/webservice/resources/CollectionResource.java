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
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.jdbi.CollectionDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.OrganizationDAO;
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
@Path("/organizations")
@Api("/organizations")
@Produces(MediaType.APPLICATION_JSON)
public class CollectionResource implements AuthenticatedResourceInterface, AliasableResourceInterface<Collection> {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationResource.class);

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for approved organizations, authentication can be provided for unapproved organizations";

    private final CollectionDAO collectionDAO;
    private final OrganizationDAO organizationDAO;
    private final WorkflowDAO workflowDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;

    public CollectionResource(SessionFactory sessionFactory) {
        this.collectionDAO = new CollectionDAO(sessionFactory);
        this.organizationDAO = new OrganizationDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
    }

    /**
     * TODO: Path looks a bit weird
     */
    @PUT
    @Timed
    @UnitOfWork
    @Override
    @Path("/collections/{collectionId}/aliases")
    @ApiOperation(nickname = "updateCollectionAliases", value = "Update the aliases linked to a Collection in Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Collection.class)
    public Collection updateAliases(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("collectionId") Long id,
        @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        return AliasableResourceInterface.super.updateAliases(user, id, aliases, emptyBody);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/collections/{alias}/aliases")
    @ApiOperation(nickname = "getCollectionByAlias", value = "Retrieves a collection by alias.", response = Collection.class)
    public Collection getCollectionByAlias(@ApiParam(value = "Alias", required = true) @PathParam("alias") String alias) {
        return this.getAndCheckResourceByAlias(alias);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections/{collectionId}")
    @ApiOperation(value = "Retrieves a collection by ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection getCollectionById(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId) {

        if (!user.isPresent()) {
            // No user given, only show collections from approved organizations
            Collection collection = collectionDAO.findById(collectionId);
            if (collection == null) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
            return getApprovalForCollection(collection);
        } else {
            // User is given, check if the collections organization is either approved or the user has access
            // Admins and curators should be able to see collections from unapproved organizations
            Collection collection = getAndCheckCollection(collectionId, user.get());
            Hibernate.initialize(collection.getEntries());
            return collection;
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections/{collectionId}/entry")
    @ApiOperation(value = "Add an entry to a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection addEntryToCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Entry ID", required = true) @QueryParam("entryId") Long entryId) {
        // Call common code to check if entry and collection exist and return them
        ImmutablePair<Entry, Collection> entryAndCollection = commonModifyCollection(entryId, collectionId, user);

        // Add the entry to the collection
        entryAndCollection.getRight().addEntry(entryAndCollection.getLeft());

        // Event for addition
        Organization organization = organizationDAO.findById(organizationId);

        Event.Builder eventBuild = new Event.Builder()
                .withOrganization(organization)
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
    @Path("{organizationId}/collections/{collectionId}/entry")
    @ApiOperation(value = "Delete an entry from a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection deleteEntryFromCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Entry ID", required = true) @QueryParam("entryId") Long entryId) {
        // Call common code to check if entry and collection exist and return them
        ImmutablePair<Entry, Collection> entryAndCollection = commonModifyCollection(entryId, collectionId, user);

        // Remove the entry from the organization
        entryAndCollection.getRight().removeEntry(entryAndCollection.getLeft());

        // Event for deletion
        Organization organization = organizationDAO.findById(organizationId);

        Event.Builder eventBuild = new Event.Builder()
                .withOrganization(organization)
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
        Collection collection = getAndCheckCollection(collectionId, user);

        // Check that user is a member of the organization
        Organization organization = organizationDAO.findById(collection.getOrganization().getId());

        OrganizationUser organizationUser = getUserOrgRole(organization, user.getId());
        if (organizationUser == null) {
            String msg = "User does not have rights to modify a collection from this organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        return new ImmutablePair<>(entry, collection);
    }

    /**
     * Get a collection and check whether user has rights to see and modify it
     * @param collectionId
     * @param user
     * @return
     */
    private Collection getAndCheckCollection(Long collectionId, User user) {
        // Check that collection exists to user
        boolean doesCollectionExist = doesCollectionExistToUser(collectionId, user.getId()) || user.getIsAdmin() || user.isCurator();
        if (!doesCollectionExist) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        return collectionDAO.findById(collectionId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections")
    @ApiOperation(value = "Retrieve all collections for an organization.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, responseContainer = "List", response = Collection.class)
    public List<Collection> getCollectionsFromOrganization(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId, @QueryParam("include") String include) {
        if (!user.isPresent()) {
            Organization organization = organizationDAO.findApprovedById(organizationId);
            if (organization == null) {
                String msg = "Organization not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
        } else {
            boolean doesOrgExist = doesOrganizationExistToUser(organizationId, user.get().getId());
            if (!doesOrgExist) {
                String msg = "Organization not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
        }

        List<Collection> collections = collectionDAO.findAllByOrg(organizationId);

        if (checkIncludes(include, "entries")) {
            collections.forEach(collection -> Hibernate.initialize(collection.getEntries()));
        }

        return collectionDAO.findAllByOrg(organizationId);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections")
    @ApiOperation(value = "Create a collection in the given organization.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection createCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection to register.", required = true) Collection collection) {

        // First check if the organization exists
        boolean doesOrgExist = doesOrganizationExistToUser(organizationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organization not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        // Check if any other collections in the organization exist with that name
        Collection matchingCollection = collectionDAO.findByNameAndOrg(collection.getName(), organizationId);
        if (matchingCollection != null) {
            String msg = "A collection already exists with the name '" + collection.getName() + "' in the specified organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Get the organization
        Organization organization = organizationDAO.findById(organizationId);

        // Save the collection
        long id = collectionDAO.create(collection);
        organization.addCollection(collection);

        // Event for creation
        User foundUser = userDAO.findById(user.getId());
        Event createCollectionEvent = new Event.Builder()
                .withOrganization(organization)
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
    @Path("{organizationId}/collections/{collectionId}")
    @ApiOperation(value = "Update a collection.", notes = "Currently only name and description can be updated.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection updateCollection(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Collection to update with.", required = true) Collection collection,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long collectionId) {
        // Ensure collection exists to the user
        Collection existingCollection = this.getAndCheckCollection(collectionId, user);
        Organization organization = organizationDAO.findById(existingCollection.getOrganization().getId());

        // Check that the user has rights to the organization
        OrganizationUser organizationUser = getUserOrgRole(organization, user.getId());
        if (organizationUser == null) {
            String msg = "User does not have rights to modify a collection from this organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        // Check if new name is valid
        if (!Objects.equals(existingCollection.getName(), collection.getName())) {
            Collection duplicateName = collectionDAO.findByNameAndOrg(collection.getName(), existingCollection.getOrganization().getId());
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
                .withOrganization(organization)
                .withCollection(existingCollection)
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_COLLECTION)
                .build();
        eventDAO.create(updateCollectionEvent);

        return collectionDAO.findById(collectionId);

    }

    /**
     * For a collection to exist to a user, it must either be from an approved organization
     * or an organization the user has access to.
     * @param collectionId
     * @param userId
     * @return True if collection exists to user, false otherwise
     */
    private boolean doesCollectionExistToUser(Long collectionId, Long userId) {
        // A collection is only visible to a user if the organization it belongs to is approved or they are a member
        Collection collection = collectionDAO.findById(collectionId);
        if (collection == null) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        return doesOrganizationExistToUser(collection.getOrganization().getId(), userId);
    }

    /**
     * Checks if the given user should know of the existence of the organization
     * For a user to see an organsation, either it must be approved or the user must have a role in the organization
     * @param organizationId
     * @param userId
     * @return True if organization exists to user, false otherwise
     */
    private boolean doesOrganizationExistToUser(Long organizationId, Long userId) {
        Organization organization = organizationDAO.findById(organizationId);
        if (organization == null) {
            return false;
        }
        OrganizationUser organizationUser = getUserOrgRole(organization, userId);
        return Objects.equals(organization.getStatus(), Organization.ApplicationState.APPROVED) || (organizationUser != null);
    }

    /**
     * Determine the role of a user in an organization
     * @param organization
     * @param userId
     * @return OrganizationUser role
     */
    private OrganizationUser getUserOrgRole(Organization organization, Long userId) {
        Set<OrganizationUser> organizationUserSet = organization.getUsers();
        Optional<OrganizationUser> matchingUser = organizationUserSet.stream().filter(organizationUser -> Objects
                .equals(organizationUser.getUser().getId(), userId)).findFirst();
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

    @Override
    public Optional<ElasticManager> getElasticManager() {
        return Optional.empty();
    }

    @Override
    public Collection getAndCheckResource(User user, Long id) {
        return this.getAndCheckCollection(id, user);
    }

    @Override
    public Collection getAndCheckResourceByAlias(String alias) {
        final Collection byAlias = this.collectionDAO.getByAlias(alias);
        if (byAlias == null) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        return getApprovalForCollection(byAlias);

    }

    private Collection getApprovalForCollection(Collection byAlias) {
        Organization organization = organizationDAO.findApprovedById(byAlias.getOrganization().getId());
        if (organization == null) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Hibernate.initialize(byAlias.getEntries());
        return byAlias;
    }
}
