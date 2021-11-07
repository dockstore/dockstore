package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.OPENAPI_JWT_SECURITY_DEFINITION_NAME;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.CollectionEntry;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ParamHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.jdbi.CategoryDAO;
import io.dockstore.webservice.jdbi.CollectionDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.OrganizationDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of collection endpoints
 * TODO: Add an endpoint that retrieves collection entries
 * @author aduncan
 */
@Path("/organizations")
@Api("/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "organizations", description = ResourceConstants.ORGANIZATIONS)
@SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearer", scheme = "bearer") })
public class CollectionResource implements AuthenticatedResourceInterface, AliasableResourceInterface<Collection> {

    private static final String ORGANIZATION_NOT_FOUND_MESSAGE = "Organization not found.";
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationResource.class);

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for approved organizations, authentication can be provided for unapproved organizations";

    private final CategoryDAO categoryDAO;
    private final CollectionDAO collectionDAO;
    private final OrganizationDAO organizationDAO;
    private final WorkflowDAO workflowDAO;
    private final ToolDAO toolDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;
    private final VersionDAO versionDAO;
    private final CollectionHelper helper;

    public CollectionResource(SessionFactory sessionFactory) {
        this.categoryDAO = new CategoryDAO(sessionFactory);
        this.collectionDAO = new CollectionDAO(sessionFactory);
        this.organizationDAO = new OrganizationDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.versionDAO = new VersionDAO(sessionFactory);
        this.helper = new CollectionHelper(sessionFactory, toolDAO);
    }

    /**
     * TODO: Path looks a bit weird
     */
    @POST
    @Timed
    @UnitOfWork
    @Override
    @Path("/collections/{collectionId}/aliases")
    @ApiOperation(nickname = "addCollectionAliases", value = "Add aliases linked to a collection in Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Collection.class)
    @Operation(operationId = "addCollectionAliases", summary = "Add aliases linked to a collection in Dockstore.", description = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added alias to collection", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Collection.class)))
    public Collection addAliases(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Collection to modify.", required = true) @Parameter(description = "Collection to modify.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long id,
        @ApiParam(value = "Comma-delimited list of aliases.", required = true) @Parameter(description = "Comma-delimited list of aliases.", name = "aliases", in = ParameterIn.QUERY, required = true) @QueryParam("aliases") String aliases) {
        return AliasableResourceInterface.super.addAliases(user, id, aliases);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/collections/{alias}/aliases")
    @ApiOperation(nickname = "getCollectionByAlias", value = "Retrieve a collection by alias.", response = Collection.class)
    @Operation(operationId = "getCollectionByAlias", summary = "Retrieve a collection by alias.", description = "Retrieve a collection by alias.")
    public Collection getCollectionByAlias(@ApiParam(value = "Alias of the collection", required = true) @Parameter(description = "Alias of the collection.", name = "alias", required = true) @PathParam("alias") String alias) {
        return this.getAndCheckResourceByAlias(alias);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{organizationId}/collections/{collectionId}")
    @ApiOperation(value = "Retrieve a collection by ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "getCollectionById", summary = "Retrieve a collection by ID.", description = "Retrieve a collection by ID. Supports optional authentication.", security = @SecurityRequirement(name = "bearer"))
    public Collection getCollectionById(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId) {

        if (user.isEmpty()) {
            // No user given, only show collections from approved organizations
            Collection collection = collectionDAO.findById(collectionId);
            throwExceptionForNullCollection(collection);
            // check that organization id matches
            if (collection.getOrganizationID() != organizationId) {
                collection = null;
            }
            throwExceptionForNullCollection(collection);
            assert collection != null;
            Hibernate.initialize(collection.getAliases());
            Collection approvalForCollection = getApprovalForCollection(collection);
            evictAndAddEntries(approvalForCollection);
            return approvalForCollection;
        } else {
            // User is given, check if the collections organization is either approved or the user has access
            // Admins and curators should be able to see collections from unapproved organizations
            Collection collection = getAndCheckCollection(Optional.of(organizationId), collectionId, user.get());
            // check that organization id matches
            if (collection.getOrganizationID() != organizationId) {
                collection = null;
                throwExceptionForNullCollection(collection);
            }
            assert collection != null;
            Hibernate.initialize(collection.getAliases());
            evictAndAddEntries(collection);
            return collection;
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{organizationName}/collections/{collectionName}/name")
    @ApiOperation(value = "Retrieve a collection by name.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "getCollectionByName", summary = "Retrieve a collection by name.", description = "Retrieve a collection by name. Supports optional authentication.", security = @SecurityRequirement(name = "bearer"))
    public Collection getCollectionByName(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
            @ApiParam(value = "Organization name.", required = true) @Parameter(description = "Organization name.", name = "organizationName", in = ParameterIn.PATH, required = true) @PathParam("organizationName") String organizationName,
            @ApiParam(value = "Collection name.", required = true) @Parameter(description = "Collection name.", name = "collectionName", in = ParameterIn.PATH, required = true) @PathParam("collectionName") String collectionName) {
        if (user.isEmpty()) {
            // No user given, only show collections from approved organizations
            Organization organization = organizationDAO.findApprovedByName(organizationName);
            if (organization == null) {
                String msg = "Organization " + Utilities.cleanForLogging(organizationName) + " not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }

            Collection collection = collectionDAO.findByNameAndOrg(collectionName, organization.getId());
            throwExceptionForNullCollection(collection);
            Collection approvalForCollection = getApprovalForCollection(collection);
            evictAndAddEntries(approvalForCollection);
            return approvalForCollection;
        } else {
            // User is given, check if the collections organization is either approved or the user has access
            // Admins and curators should be able to see collections from unapproved organizations
            Organization organization = organizationDAO.findByName(organizationName);
            if (organization == null || !OrganizationResource.doesOrganizationExistToUser(organization.getId(), user.get().getId(), organizationDAO, userDAO)) {
                String msg = "Organization " + Utilities.cleanForLogging(organizationName) + " not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }

            Collection collection = collectionDAO.findByNameAndOrg(collectionName, organization.getId());
            throwExceptionForNullCollection(collection);

            Hibernate.initialize(collection.getAliases());
            evictAndAddEntries(collection);
            return collection;
        }
    }

    private void evictAndSummarize(Collection collection) {
        helper.evictAndSummarize(collection);
    }

    private void evictAndAddEntries(Collection collection) {
        helper.evictAndAddEntries(collection);
    }

    private void throwExceptionForNullCollection(Collection collection) {
        helper.throwExceptionForNullCollection(collection);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections/{collectionId}/entry")
    @ApiOperation(value = "Add an entry to a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "addEntryToCollection", summary = "Add an entry to a collection.", description = "Add an entry to a collection.", security = @SecurityRequirement(name = "bearer"))
    public Collection addEntryToCollection(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Entry ID", required = true) @Parameter(description = "Entry ID.", name = "entryId", in = ParameterIn.QUERY, required = true) @QueryParam("entryId") Long entryId,
            @ApiParam(value = "Version ID", required = false) @Parameter(description = "Version ID.", name = "versionId", in = ParameterIn.QUERY, required = false) @QueryParam("versionId") Long versionId) {
        // Call common code to check if entry and collection exist and return them
        ImmutablePair<Entry, Collection> entryAndCollection = commonModifyCollection(organizationId, entryId, collectionId, user);
        if (versionId == null) {
            // Add the entry to the collection
            entryAndCollection.getRight().addEntry(entryAndCollection.getLeft(), null);
        } else {
            // TODO: Need to check that the version belongs to the entry
            Version version = versionDAO.findById(versionId);
            entryAndCollection.getRight().addEntry(entryAndCollection.getLeft(), version);
        }

        // Event for addition
        Organization organization = organizationDAO.findById(organizationId);

        Event.Builder eventBuild = new Event.Builder()
                .withOrganization(organization)
                .withCollection(entryAndCollection.getRight())
                .withInitiatorUser(user)
                .withType(Event.EventType.ADD_TO_COLLECTION);

        if (entryAndCollection.getLeft() instanceof BioWorkflow) {
            eventBuild = eventBuild.withBioWorkflow((BioWorkflow)entryAndCollection.getLeft());
        } else if (entryAndCollection.getLeft() instanceof Service) {
            eventBuild = eventBuild.withService((Service)entryAndCollection.getLeft());
        } else if (entryAndCollection.getLeft() instanceof Tool) {
            eventBuild = eventBuild.withTool((Tool)entryAndCollection.getLeft());
        }

        Event addToCollectionEvent = eventBuild.build();
        eventDAO.create(addToCollectionEvent);

        // If added to a Category, update the Entry in the index
        if (entryAndCollection.getRight() instanceof Category) {
            handleIndexUpdate(entryAndCollection.getLeft());
        }

        return collectionDAO.findById(collectionId);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections/{collectionId}/entry")
    @ApiOperation(value = "Delete an entry from a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "deleteEntryFromCollection", summary = "Delete an entry to a collection.", description = "Delete an entry to a collection.", security = @SecurityRequirement(name = "bearer"))
    public Collection deleteEntryFromCollection(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Entry ID", required = true) @Parameter(description = "Entry ID.", name = "entryId", in = ParameterIn.QUERY, required = true) @QueryParam("entryId") Long entryId,
            @ApiParam(value = "Version ID", required = false) @Parameter(description = "Version ID.", name = "versionId", in = ParameterIn.QUERY, required = false) @QueryParam("versionName") String versionName) {
        // Call common code to check if entry and collection exist and return them
        ImmutablePair<Entry, Collection> entryAndCollection = commonModifyCollection(organizationId, entryId, collectionId, user);

        Long versionId = null;
        if (versionName != null) {
            Set<Version> workflowVersions = entryAndCollection.getLeft().getWorkflowVersions();

            Optional<Version> first = workflowVersions.stream().filter(version -> version.getName().equals(versionName)).findFirst();
            if (first.isPresent()) {
                versionId = first.get().getId();
            } else {
                throw new CustomWebApplicationException("Version not found", HttpStatus.SC_NOT_FOUND);
            }
        }

        // Remove the entry from the organization,
        // This silently fails if the user somehow manages to give a non-existent entryId and versionId pair
        entryAndCollection.getRight().removeEntry(entryAndCollection.getLeft().getId(), versionId);

        // Event for deletion
        Organization organization = organizationDAO.findById(organizationId);

        Event.Builder eventBuild = new Event.Builder()
                .withOrganization(organization)
                .withCollection(entryAndCollection.getRight())
                .withInitiatorUser(user)
                .withType(Event.EventType.REMOVE_FROM_COLLECTION);

        if (entryAndCollection.getLeft() instanceof BioWorkflow) {
            eventBuild = eventBuild.withBioWorkflow((BioWorkflow)entryAndCollection.getLeft());
        } else if (entryAndCollection.getLeft() instanceof Service) {
            eventBuild = eventBuild.withService((Service)entryAndCollection.getLeft());
        } else if (entryAndCollection.getLeft() instanceof Tool) {
            eventBuild = eventBuild.withTool((Tool)entryAndCollection.getLeft());
        }

        Event removeFromCollectionEvent = eventBuild.build();
        eventDAO.create(removeFromCollectionEvent);

        // If deleted from a Category, update the Entry in the index
        if (entryAndCollection.getRight() instanceof Category) {
            handleIndexUpdate(entryAndCollection.getLeft());
        }

        return collectionDAO.findById(collectionId);
    }

    /**
     * Common code used to add and delete from a collection. Will ensure that both the entry and collection are
     * visible to the user and that the user has the correct credentials to edit the collection.
     * @param entryId the entry to add, will be considered a bad request if the entry doesn't exist
     * @param collectionId the collection to modify, will be considered a not found exception if the collection doesn't exist
     * @param user User performing the action
     * @return Pair of found Entry and Collection
     */
    private ImmutablePair<Entry, Collection> commonModifyCollection(Long organizationId, Long entryId, Long collectionId, User user) {
        // Check that entry exists (could use either workflowDAO or toolDAO here)
        // Note that only published entries can be added
        Entry<? extends Entry, ? extends Version> entry = workflowDAO.getGenericEntryById(entryId);
        if (entry == null || !entry.getIsPublished()) {
            String msg = "Entry not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        Collection collection = getAndCheckCollection(Optional.of(organizationId), collectionId, user);

        // Check that user is an admin or maintainer of the organization
        getOrganizationAndCheckModificationRights(user, collection);
        return new ImmutablePair<>(entry, collection);
    }

    /**
     * Get a collection and check whether user has rights to see and modify it
     * @param organizationId (provide as an optional check)
     * @param collectionId
     * @param user
     * @return
     */
    private Collection getAndCheckCollection(Optional<Long> organizationId, Long collectionId, User user) {
        // Check that collection exists to user
        final Collection collection = collectionDAO.findById(collectionId);
        boolean doesCollectionExist = doesCollectionExistToUser(collection, user.getId()) || user.getIsAdmin() || user.isCurator();
        if (!doesCollectionExist) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        // if organizationId is provided, check it
        if (organizationId.isPresent() && organizationId.get() != collection.getOrganizationID()) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        return collection;
    }


    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{organizationId}/collections")
    @ApiOperation(value = "Retrieve all collections for an organization.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, responseContainer = "List", response = Collection.class)
    @Operation(operationId = "getCollectionsFromOrganization", summary = "Retrieve all collections for an organization.", description = "Retrieve all collections for an organization. Supports optional authentication.", security = @SecurityRequirement(name = "bearer"))
    public List<Collection> getCollectionsFromOrganization(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Included fields") @Parameter(description = "Included fields.", name = "include", in = ParameterIn.QUERY, required = true) @QueryParam("include") String include) {
        if (user.isEmpty()) {
            Organization organization = organizationDAO.findApprovedById(organizationId);
            throwExceptionForNullOrganization(organization);
        } else {
            boolean doesOrgExist = OrganizationResource.doesOrganizationExistToUser(organizationId, user.get().getId(), organizationDAO, userDAO);
            if (!doesOrgExist) {
                String msg = ORGANIZATION_NOT_FOUND_MESSAGE;
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
        }

        List<Collection> collections = collectionDAO.findAllByOrg(organizationId);
        boolean includeEntries = ParamHelper.csvIncludesField(include, "entries");
        collections.forEach(collection -> {
            if (includeEntries) {
                evictAndAddEntries(collection);
            } else {
                evictAndSummarize(collection);
            }
        });
        return collections;
    }

    private void throwExceptionForNullOrganization(Organization organization) {
        if (organization == null) {
            String msg = ORGANIZATION_NOT_FOUND_MESSAGE;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Consumes("application/json")
    @Path("{organizationId}/collections")
    @ApiOperation(value = "Create a collection in the given organization.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "createCollection", summary = "Create a collection in the given organization.", description = "Create a collection in the given organization.", security = @SecurityRequirement(name = "bearer"))
    public Collection createCollection(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection to register.", required = true) @Parameter(description = "Collection to register.", name = "collection", required = true) Collection collection) {

        // First check if the organization exists and that the user is an admin or maintainer
        boolean isUserAdminOrMaintainer = OrganizationResource.isUserAdminOrMaintainer(organizationId, user.getId(), organizationDAO);
        if (!isUserAdminOrMaintainer) {
            String msg = ORGANIZATION_NOT_FOUND_MESSAGE;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        // Get the organization
        Organization organization = organizationDAO.findById(organizationId);
        if (organization == null) {
            String msg = ORGANIZATION_NOT_FOUND_MESSAGE;
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

        matchingCollection = collectionDAO.findByDisplayNameAndOrg(collection.getDisplayName(), organizationId);
        if (matchingCollection != null) {
            String msg = "A collection already exists with the display name '" + collection.getDisplayName() + "' in the specified organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        final Collection collectionOrCategory;

        if (organization.isCategorizer()) {
            // The organization is a categorizer, make sure there are no category name collisions and convert the Collection to a Category
            Category matchingCategory = categoryDAO.findByName(collection.getName());
            if (matchingCategory != null) {
                String msg = "A category already exists with the name '" + collection.getName() + "'.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
            collectionOrCategory = constructCategory(collection);
        } else {
            collectionOrCategory = collection;
        }

        // Save the collection
        long id = collectionDAO.create(collectionOrCategory);
        organization.addCollection(collectionOrCategory);

        // Event for creation
        User foundUser = userDAO.findById(user.getId());
        Event createCollectionEvent = new Event.Builder()
                .withOrganization(organization)
                .withCollection(collectionOrCategory)
                .withInitiatorUser(foundUser)
                .withType(Event.EventType.CREATE_COLLECTION)
                .build();
        eventDAO.create(createCollectionEvent);

        return collectionDAO.findById(id);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Consumes("application/json")
    @Path("{organizationId}/collections/{collectionId}")
    @ApiOperation(value = "Update a collection.", notes = "Currently only name, display name, description, and topic can be updated.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "updateCollection", summary = "Update a collection.", description = "Update a collection. Currently only name, display name, description, and topic can be updated.", security = @SecurityRequirement(name = "bearer"))
    public Collection updateCollection(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @ApiParam(value = "Collection to update with.", required = true) @Parameter(description = "Collection to register.", name = "collection", required = true) Collection collection,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId) {
        // Ensure collection exists to the user
        Collection existingCollection = this.getAndCheckCollection(Optional.of(organizationId), collectionId, user);
        Organization organization = getOrganizationAndCheckModificationRights(user, existingCollection);

        // Check if new name is valid
        if (!Objects.equals(existingCollection.getName(), collection.getName())) {
            boolean isCategory = existingCollection instanceof Category;
            Collection duplicateCollection;
            if (isCategory) {
                duplicateCollection = categoryDAO.findByName(collection.getName());
            } else {
                duplicateCollection = collectionDAO.findByNameAndOrg(collection.getName(), existingCollection.getOrganization().getId());
            }
            if (duplicateCollection != null) {
                if (duplicateCollection.getId() == existingCollection.getId()) {
                    // do nothing
                    LOG.debug("this appears to be a case change");
                } else {
                    String msg = "A " + (isCategory ? "category" : "collection") + " already exists with the name '" + collection.getName() + "', please try another one.";
                    LOG.info(msg);
                    throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
                }
            }
        }

        // Update the collection
        existingCollection.setName(collection.getName());
        existingCollection.setDisplayName(collection.getDisplayName());
        existingCollection.setDescription(collection.getDescription());
        existingCollection.setTopic(collection.getTopic());

        // Event for update
        Event updateCollectionEvent = new Event.Builder()
                .withOrganization(organization)
                .withCollection(existingCollection)
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_COLLECTION)
                .build();
        eventDAO.create(updateCollectionEvent);

        // If we are updating a Category, assume a property has changed and reindex the Entries.
        if (existingCollection instanceof Category) {
            reindexEntries(existingCollection);
        }

        return collectionDAO.findById(collectionId);

    }

    private void reindexEntries(Collection collection) {
        long id = collection.getId();

        // Accumulate the entry,version combos, there can be multiple different versions per entry.
        List<CollectionEntry> collectionEntries = new ArrayList<>();
        collectionEntries.addAll(toolDAO.getCollectionWorkflows(id));
        collectionEntries.addAll(toolDAO.getCollectionWorkflowsWithVersions(id));
        collectionEntries.addAll(toolDAO.getCollectionTools(id));
        collectionEntries.addAll(toolDAO.getCollectionToolsWithVersions(id));
        collectionEntries.addAll(toolDAO.getCollectionServices(id));
        collectionEntries.addAll(toolDAO.getCollectionServicesWithVersions(id));

        // Reduce to a set of entries and index.
        Set<Entry> entries = new HashSet<>();
        collectionEntries.forEach(collectionEntry -> entries.add(toolDAO.getGenericEntryById(collectionEntry.getId())));
        entries.forEach(this::handleIndexUpdate);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("{organizationId}/collections/{collectionId}")
    @ApiOperation(value = "Delete a collection.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, hidden = true)
    @Operation(operationId = "deleteCollection", summary = "Delete a collection.", description = "Delete a collection.", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "Successfully deleted the collection")
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = "Collection not found")
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = "Unauthorized")
    public void deleteCollection(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
        @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId) {
        // Ensure collection exists to the user
        Collection collection = this.getAndCheckCollection(Optional.of(organizationId), collectionId, user);
        Organization organization = getOrganizationAndCheckModificationRights(user, collection);

        // Soft delete the collection
        collection.setDeleted(true);

        // If the collection was a Category, reindex the entries.
        if (collection instanceof Category) {
            reindexEntries(collection);
        }

        // Create the delete collection event.
        Event.Builder eventBuild = new Event.Builder()
                .withOrganization(organization)
                .withCollection(collection)
                .withInitiatorUser(user)
                .withType(Event.EventType.DELETE_COLLECTION);

        Event deleteCollectionEvent = eventBuild.build();
        eventDAO.create(deleteCollectionEvent);
    }

    @PUT
    @Timed
    @Path("{organizationId}/collections/{collectionId}/description")
    @UnitOfWork
    @ApiOperation(value = "Update a collection's description.", notes = "Description in markdown", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    @Operation(operationId = "updateCollectionDescription", summary = "Update a collection's description.", description = "Update a collection's description. Description in markdown.", security = @SecurityRequirement(name = "bearer"))
    public Collection updateCollectionDescription(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID.", required = true) @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId,
            @ApiParam(value = "Collections's description in markdown.", required = true) @Parameter(description = "Collections's description in markdown.", name = "description", required = true) String description) {
        Collection oldCollection = this.getAndCheckCollection(Optional.of(organizationId), collectionId, user);
        Organization oldOrganization = getOrganizationAndCheckModificationRights(user, oldCollection);

        // Update collection
        oldCollection.setDescription(description);

        Event updateCollectionEvent = new Event.Builder()
                .withOrganization(oldOrganization)
                .withCollection(oldCollection)
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_ORG)
                .build();
        eventDAO.create(updateCollectionEvent);

        if (oldCollection instanceof Category) {
            reindexEntries(oldCollection);
        }

        return collectionDAO.findById(collectionId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{organizationId}/collections/{collectionId}/description")
    @ApiOperation(value = "Retrieve a collection description by organization ID and collection ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = String.class)
    @Operation(operationId = "getCollectionDescription", summary = "Retrieve a collection description by organization ID and collection ID.", description = "Retrieve a collection description by organization ID and collection ID. Supports optional authentication.", security = @SecurityRequirement(name = "bearer"))
    public String getCollectionDescription(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
            @ApiParam(value = "Organization ID", required = true) @Parameter(description = "Organization ID.", name = "organizationId", in = ParameterIn.PATH, required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Collection ID", required = true) @Parameter(description = "Collection ID.", name = "collectionId", in = ParameterIn.PATH, required = true) @PathParam("collectionId") Long collectionId) {
        return getCollectionById(user, organizationId, collectionId).getDescription();
    }

    private Organization getOrganizationAndCheckModificationRights(User user, Collection existingCollection) {
        Organization organization = organizationDAO.findById(existingCollection.getOrganization().getId());

        // Check that the user has rights to the organization
        OrganizationUser organizationUser = OrganizationResource.getUserOrgRole(organization, user.getId());
        if (organizationUser == null || organizationUser.getRole() == OrganizationUser.Role.MEMBER) {
            String msg = "User does not have rights to modify a collection from this organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }
        return organization;
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
        return doesCollectionExistToUser(collection, userId);
    }

    private boolean doesCollectionExistToUser(Collection collection, Long userId) {
        if (collection == null) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        return OrganizationResource.doesOrganizationExistToUser(collection.getOrganization().getId(), userId, organizationDAO, userDAO);
    }

    @Override
    public Optional<PublicStateManager> getPublicStateManager() {
        return Optional.empty();
    }

    @Override
    public Collection getAndCheckResource(User user, Long id) {
        return this.getAndCheckCollection(Optional.empty(), id, user);
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

        Hibernate.initialize(byAlias.getAliases());
        return byAlias;
    }

    private void handleIndexUpdate(Entry entry) {
        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);
    }

    private Category constructCategory(Collection collection) {
        Category category = new Category();
        category.setName(collection.getName());
        category.setDescription(collection.getDescription());
        category.setDisplayName(collection.getDisplayName());
        category.setTopic(collection.getTopic());
        category.setOrganization(collection.getOrganization());
        return category;
    }
}
