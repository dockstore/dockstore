package io.dockstore.webservice.resources;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Organisation;
import io.dockstore.webservice.core.OrganisationUser;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.CollectionDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.OrganisationDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * Collection of collection endpoints
 * @author aduncan
 */
@Path("/collections")
@Api("/collections")
@Produces(MediaType.APPLICATION_JSON)
public class CollectionResource implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(OrganisationResource.class);

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for approved organisations, authentication can be provided for unapproved organisations";

    private final CollectionDAO collectionDAO;
    private final OrganisationDAO organisationDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;
    private final SessionFactory sessionFactory;

    public CollectionResource(SessionFactory sessionFactory) {
        this.collectionDAO = new CollectionDAO(sessionFactory);
        this.organisationDAO = new OrganisationDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{collectionId}")
    @ApiOperation(value = "Retrieves a collection by ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Collection.class)
    public Collection getCollectionById(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Collection ID.", required = true) @PathParam("collectionId") Long id) {

        if (!user.isPresent()) {
            // No user given, only show collections from approved organisations
            Collection collection = collectionDAO.findById(id);
            if (collection == null) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
            Organisation organisation = organisationDAO.findApprovedById(collection.getOrganisation().getId());
            if (organisation == null) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }

            return collection;
        } else {
            // User is given, check if the collections organisation is either approved or the user has access
            // Admins and curators should be able to see collections from unapproved organisations
            boolean doesCollectionExist = doesCollectionExistToUser(id, user.get().getId()) || user.get().getIsAdmin() || user.get().isCurator();
            if (!doesCollectionExist) {
                String msg = "Collection not found.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }

            Collection collection = collectionDAO.findById(id);
            return collection;
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/create/{organisationId}")
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
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
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
        Event createCollectionEvent = new Event(null, organisation, collection, null, null, foundUser, Event.EventType.CREATE_COLLECTION);
        eventDAO.create(createCollectionEvent);

        return collectionDAO.findById(id);
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
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
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
        if (matchingUser.isPresent()) {
            return matchingUser.get();
        } else {
            return null;
        }
    }
}
