package io.dockstore.webservice.resources;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
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
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.OrganizationDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * Collection of organization endpoints
 * @author aduncan
 */
@Path("/organizations")
@Api("/organizations")
@Produces(MediaType.APPLICATION_JSON)
public class OrganizationResource implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationResource.class);

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for approved organizations, authentication can be provided for unapproved organizations";

    private final OrganizationDAO organizationDAO;
    private final UserDAO userDAO;
    private final EventDAO eventDAO;
    private final SessionFactory sessionFactory;

    public OrganizationResource(SessionFactory sessionFactory) {
        this.organizationDAO = new OrganizationDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all available organizations.", notes = "NO Authentication", responseContainer = "List", response = Organization.class)
    public List<Organization> getApprovedOrganizations() {
        return organizationDAO.findAllApproved();
    }

    @POST
    @Timed
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @Path("{organizationId}/approve/")
    @ApiOperation(value = "Approves an organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin/curator only", response = Organization.class)
    public Organization approveOrganization(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {
        Organization organization = organizationDAO.findById(id);
        if (organization == null) {
            String msg = "Organization not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        if (!Objects.equals(organization.getStatus(), Organization.ApplicationState.APPROVED)) {
            organization.setStatus(Organization.ApplicationState.APPROVED);

            Event approveOrgEvent = new Event.Builder()
                    .withOrganization(organization)
                    .withInitiatorUser(user)
                    .withType(Event.EventType.APPROVE_ORG)
                    .build();
            eventDAO.create(approveOrgEvent);
        }

        return organizationDAO.findById(id);
    }

    @POST
    @Timed
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @Path("{organizationId}/reject/")
    @ApiOperation(value = "Rejects an organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin/curator only", response = Organization.class)
    public Organization rejectOrganization(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {
        Organization organization = organizationDAO.findById(id);
        if (organization == null) {
            String msg = "Organization not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        if (Objects.equals(organization.getStatus(), Organization.ApplicationState.PENDING)) {
            organization.setStatus(Organization.ApplicationState.REJECTED);

            Event rejectOrgEvent = new Event.Builder()
                    .withOrganization(organization)
                    .withInitiatorUser(user)
                    .withType(Event.EventType.REJECT_ORG)
                    .build();
            eventDAO.create(rejectOrgEvent);
        } else if (Objects.equals(organization.getStatus(), Organization.ApplicationState.APPROVED)) {
            String msg = "The organization is already approved";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        return organizationDAO.findById(id);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/name/{name}/")
    @ApiOperation(value = "Retrieves an organization by name.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class)
    public Organization getOrganizationByName(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization name.", required = true) @PathParam("name") String name) {
        if (!user.isPresent()) {
            // No user given, only show approved organizations
            Organization organization = organizationDAO.findApprovedByName(name);
            if (organization == null) {
                String msg = "Organization not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
            return organization;
        } else {
            // User is given, check if organization is either approved or the user has access
            // Admins and curators should be able to see unapproved organizations
            Organization organization = organizationDAO.findByName(name);
            if (organization == null) {
                String msg = "Organization not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }

            // If approved then return
            if (Objects.equals(organization.getStatus(), Organization.ApplicationState.APPROVED)) {
                return organization;
            }

            // If user has a role then return
            OrganizationUser role = getUserOrgRole(organization, user.get().getId());

            if (user.get().getIsAdmin() || user.get().isCurator() || role != null) {
                return organization;
            } else {
                String msg = "Organization not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{organizationId}")
    @ApiOperation(value = "Retrieves an organization by ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class)
    public Organization getOrganizationById(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {
        return getOrganizationByIdOptionalAuth(user, id);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/description")
    @ApiOperation(value = "Retrieves an organization description by organization ID.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = String.class)
    public String getOrganizationDescription(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {
        return getOrganizationByIdOptionalAuth(user, id).getDescription();
    }

    @PUT
    @Timed
    @Path("{organizationId}/description")
    @UnitOfWork
    @ApiOperation(value = "Update an organization's description.", notes = "Description in markdown", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class)
    public Organization updateOrganizationDescription(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization to update description.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Organization's description in markdown", required = true) String description) {

        boolean doesOrgExist = doesOrganizationExistToUser(organizationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organization not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Organization oldOrganization = organizationDAO.findById(organizationId);

        // Ensure that the user is a member of the organization
        OrganizationUser organizationUser = getUserOrgRole(oldOrganization, user.getId());
        if (organizationUser == null) {
            String msg = "You do not have permissions to update the organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        // Update organization
        oldOrganization.setDescription(description);

        Event updateOrganizationEvent = new Event.Builder()
                .withOrganization(oldOrganization)
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_ORG)
                .build();
        eventDAO.create(updateOrganizationEvent);

        return organizationDAO.findById(organizationId);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/members")
    @ApiOperation(value = "Retrieves all members for an organization.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganizationUser.class, responseContainer = "Set")
    public Set<OrganizationUser> getOrganizationMembers(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {
        return getOrganizationByIdOptionalAuth(user, id).getUsers();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/events")
    @ApiOperation(value = "Retrieves all events for an organization.", notes = OPTIONAL_AUTH_MESSAGE, authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Event.class, responseContainer = "List")
    public List<Event> getOrganizationEvents(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {
        getOrganizationByIdOptionalAuth(user, id);
        return eventDAO.findEventsForOrganization(id);
    }

    /**
     * Retrieve an organization using optional authentication
     * @param user Optional user to authenticate with
     * @param id Organization id
     * @return Organization with given id
     */
    private Organization getOrganizationByIdOptionalAuth(Optional<User> user, Long id) {
        if (!user.isPresent()) {
            // No user given, only show approved organizations
            Organization organization = organizationDAO.findApprovedById(id);
            if (organization == null) {
                String msg = "Organization not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }
            return organization;
        } else {
            // User is given, check if organization is either approved or the user has access
            // Admins and curators should be able to see unapproved organizations
            boolean doesOrgExist = doesOrganizationExistToUser(id, user.get().getId()) || user.get().getIsAdmin() || user.get().isCurator();
            if (!doesOrgExist) {
                String msg = "Organization not found";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
            }

            Organization organization = organizationDAO.findById(id);
            return organization;
        }
    }


    @GET
    @Timed
    @UnitOfWork
    @Path("/all")
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "List all organizations.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin/curator only", responseContainer = "List", response = Organization.class)
    public List<Organization> getAllOrganizations(@ApiParam(value = "Filter to apply to organizations.", required = true, allowableValues = "all, pending, rejected, approved") @QueryParam("type") String type) {
        List<Organization> organizations;

        switch (type) {
        case "pending":
            organizations = organizationDAO.findAllPending();
            break;
        case "rejected":
            organizations = organizationDAO.findAllRejected();
            break;
        case "approved":
            organizations = organizationDAO.findAllApproved();
            break;
        case "all": default:
            organizations = organizationDAO.findAll();
            break;
        }

        organizations.forEach(organization -> Hibernate.initialize(organization.getUsers()));
        return organizations;
    }

    @PUT
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Create an organization.", notes = "Organization requires approval by an admin before being made public.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class)
    public Organization createOrganization(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization to register.", required = true) Organization organization) {

        // Check if any other organizations exist with that name
        Organization matchingOrg = organizationDAO.findByName(organization.getName());
        if (matchingOrg != null) {
            String msg = "An organization already exists with the name '" + organization.getName() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Save organization
        organization.setStatus(Organization.ApplicationState.PENDING); // should not be approved by default
        long id = organizationDAO.create(organization);

        User foundUser = userDAO.findById(user.getId());

        // Create Role for user creating the organization
        OrganizationUser organizationUser = new OrganizationUser(foundUser, organizationDAO.findById(id), OrganizationUser.Role.MAINTAINER);
        organizationUser.setAccepted(true);
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.persist(organizationUser);

        Event createOrganizationEvent = new Event.Builder()
                .withOrganization(organization)
                .withInitiatorUser(foundUser)
                .withType(Event.EventType.CREATE_ORG)
                .build();
        eventDAO.create(createOrganizationEvent);

        return organizationDAO.findById(id);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("{organizationId}")
    @ApiOperation(value = "Update an organization.", notes = "Currently only name, description, email, link and location can be updated.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organization.class)
    public Organization updateOrganization(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization to update with.", required = true) Organization organization,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long id) {

        boolean doesOrgExist = doesOrganizationExistToUser(id, user.getId());
        if (!doesOrgExist) {
            String msg = "Organization not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Organization oldOrganization = organizationDAO.findById(id);

        // Ensure that the user is a member of the organization
        OrganizationUser organizationUser = getUserOrgRole(oldOrganization, user.getId());
        if (organizationUser == null) {
            String msg = "You do not have permissions to update the organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        // Check if new name is valid
        if (!Objects.equals(oldOrganization.getName(), organization.getName())) {
            Organization duplicateName = organizationDAO.findByName(organization.getName());
            if (duplicateName != null) {
                String msg = "An organization already exists with the name '" + organization.getName() + "', please try another one.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
        }

        // Update organization
        oldOrganization.setName(organization.getName());
        oldOrganization.setDisplayName(organization.getDisplayName());
        oldOrganization.setDescription(organization.getDescription());
        oldOrganization.setTopic(organization.getTopic());
        oldOrganization.setEmail(organization.getEmail());
        oldOrganization.setLink(organization.getLink());
        oldOrganization.setLocation(organization.getLocation());

        Event updateOrganizationEvent = new Event.Builder()
                .withOrganization(oldOrganization)
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_ORG)
                .build();
        eventDAO.create(updateOrganizationEvent);

        return organizationDAO.findById(id);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/users/{username}")
    @ApiOperation(value = "Adds a user role to an organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganizationUser.class)
    public OrganizationUser addUserToOrgByUsername(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Role of user. \"MAINTAINER\" or \"MEMBER\"", required = true) String role,
            @ApiParam(value = "User to add to org.", required = true) @PathParam("username") String username,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId) {
        User userToAdd = userDAO.findByUsername(username);
        if (userToAdd == null) {
            String msg = "No user exists with the username '" + username + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        return addUserToOrg(user, role, userToAdd.getId(), organizationId, "");
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/user")
    @ApiOperation(value = "Adds a user role to an organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganizationUser.class)
    public OrganizationUser addUserToOrg(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Role of user.", required = true, allowableValues = "MAINTAINER, MEMBER") @QueryParam("role") String role,
            @ApiParam(value = "User to add to org.", required = true) @QueryParam("userId") Long userId,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {

        // Basic checks to ensure that action can be taken
        Pair<Organization, User> organizationAndUserToAdd = commonUserOrg(organizationId, userId, user);

        // Check for existing roles the user has
        OrganizationUser existingRole = getUserOrgRole(organizationAndUserToAdd.getLeft(), userId);
        OrganizationUser organizationUser = null;
        if (existingRole == null) {
            organizationUser = new OrganizationUser(organizationAndUserToAdd.getRight(), organizationAndUserToAdd.getLeft(), OrganizationUser.Role.valueOf(role));
            Session currentSession = sessionFactory.getCurrentSession();
            currentSession.persist(organizationUser);
        } else {
            updateUserRole(user, role, userId, organizationId);
        }

        Event addUserOrganizationEvent = new Event.Builder()
                .withUser(organizationAndUserToAdd.getRight())
                .withOrganization(organizationAndUserToAdd.getLeft())
                .withInitiatorUser(user)
                .withType(Event.EventType.ADD_USER_TO_ORG)
                .build();
        eventDAO.create(addUserOrganizationEvent);

        return organizationUser;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/user")
    @ApiOperation(value = "Updates a user role in an organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = OrganizationUser.class)
    public OrganizationUser updateUserRole(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Role of user.", required = true, allowableValues = "MAINTAINER, MEMBER") @QueryParam("role") String role,
            @ApiParam(value = "User to add to org.", required = true) @QueryParam("userId") Long userId,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId) {

        // Basic checks to ensure that action can be taken
        Pair<Organization, User> organizationAndUserToUpdate = commonUserOrg(organizationId, userId, user);

        // Check for existing roles the user has
        OrganizationUser existingRole = getUserOrgRole(organizationAndUserToUpdate.getLeft(), userId);
        if (existingRole == null) {
            String msg = "The user with id '" + userId + "' does not have a role in the organization with id '" + organizationId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        } else {
            existingRole.setRole(OrganizationUser.Role.valueOf(role));
        }

        Event updateUserOrganizationEvent = new Event.Builder()
                .withUser(organizationAndUserToUpdate.getRight())
                .withOrganization(organizationAndUserToUpdate.getLeft())
                .withInitiatorUser(user)
                .withType(Event.EventType.MODIFY_USER_ROLE_ORG)
                .build();

        eventDAO.create(updateUserOrganizationEvent);

        return existingRole;
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/user")
    @ApiOperation(value = "Remove a user from an organization.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = void.class)
    public void deleteUserRole(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "User to add to org.", required = true) @QueryParam("userId") Long userId,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId) {

        // Basic checks to ensure that action can be taken
        Pair<Organization, User> organizationAndUserToDelete = commonUserOrg(organizationId, userId, user);

        // Check for existing roles the user has
        OrganizationUser existingRole = getUserOrgRole(organizationAndUserToDelete.getLeft(), userId);
        if (existingRole == null) {
            String msg = "The user with id '" + userId + "' does not have a role in the organization with id '" + organizationId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        } else {
            Session currentSession = sessionFactory.getCurrentSession();
            currentSession.delete(existingRole);
        }

        Event deleteUserOrganizationEvent = new Event.Builder()
                .withUser(organizationAndUserToDelete.getRight())
                .withOrganization(organizationAndUserToDelete.getLeft())
                .withInitiatorUser(user)
                .withType(Event.EventType.REMOVE_USER_FROM_ORG)
                .build();
        eventDAO.create(deleteUserOrganizationEvent);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{organizationId}/invitation")
    @ApiOperation(value = "Accept or reject an organization invitation.", notes = "True accepts the invitation, false rejects the invitation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public void acceptOrRejectInvitation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organization ID.", required = true) @PathParam("organizationId") Long organizationId,
            @ApiParam(value = "Accept or reject", required = true) @QueryParam("accept") boolean accept) {

        // Check that the organization exists
        boolean doesOrgExist = doesOrganizationExistToUser(organizationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organization not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Organization organization = organizationDAO.findById(organizationId);

        // Check that the role exists
        OrganizationUser organizationUser = getUserOrgRole(organization, user.getId());
        if (organizationUser == null) {
            String msg = "The user with id '" + user.getId() + "' does not have a role in the organization with id '" + organization.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        }

        // Check that the role is not already accepted
        if (organizationUser.isAccepted()) {
            String msg = "The user with id '" + user.getId() + "' has already accepted a role in the organization with id '" + organization.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        if (accept) {
            // Set to accepted if true
            organizationUser.setAccepted(true);
        } else {
            // Delete role if false
            Session currentSession = sessionFactory.getCurrentSession();
            currentSession.delete(organizationUser);
        }

        Event.EventType eventType = accept ? Event.EventType.APPROVE_ORG_INVITE : Event.EventType.REJECT_ORG_INVITE;
        Event addUserOrganizationEvent = new Event.Builder()
                .withOrganization(organization)
                .withInitiatorUser(user)
                .withType(eventType)
                .build();
        eventDAO.create(addUserOrganizationEvent);
    }

    /**
     * Determine the role of a user in an organization
     * @param organization
     * @param userId
     * @return OrganizationUser role
     */
    private OrganizationUser getUserOrgRole(Organization organization, Long userId) {
        Set<OrganizationUser> organizationUserSet = organization.getUsers();
        Optional<OrganizationUser> matchingUser = organizationUserSet
            .stream().filter(organizationUser -> Objects.equals(organizationUser.getUser().getId(), userId)).findFirst();
        if (matchingUser.isPresent()) {
            return matchingUser.get();
        } else {
            return null;
        }
    }

    /**
     * Checks if a user has the given role type in the organization
     * Throws an error if the user has no roles or the wrong roles
     * @param organization
     * @param userId
     * @return Role for the organizationUser
     */
    private OrganizationUser checkUserOrgRole(Organization organization, Long userId, OrganizationUser.Role role) {
        OrganizationUser organizationUser = getUserOrgRole(organization, userId);
        if (organizationUser == null) {
            String msg = "The user with id '" + userId + "' does not have a role in the organization with id '" + organization.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        } else if (!Objects.equals(organizationUser.getRole(), role)) {
            String msg = "The user with id '" + userId + "' does not have the required role in the organization with id '" + organization.getId() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_UNAUTHORIZED);
        } else {
            return organizationUser;
        }
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
     * Common checks done by the user add/edit/delete endpoints
     * @param organizationId
     * @param userId
     * @param user
     * @return A pair of organistion to edit and user add/edit/delete role
     */
    private Pair<Organization, User> commonUserOrg(Long organizationId, Long userId, User user) {
        // Check that the organization exists
        boolean doesOrgExist = doesOrganizationExistToUser(organizationId, user.getId());
        if (!doesOrgExist) {
            String msg = "Organization not found";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        Organization organization = organizationDAO.findById(organizationId);

        // Check that the user exists
        User userToAdd = userDAO.findById(userId);
        if (userToAdd == null) {
            String msg = "No user exists with the ID '" + userId + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }

        // Check that you are not applying action on yourself
        if (Objects.equals(user.getId(), userId)) {
            String msg = "You cannot modify yourself in an organization.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Ensure that the calling user is a maintainer of the organization
        checkUserOrgRole(organization, user.getId(), OrganizationUser.Role.MAINTAINER);

        return new ImmutablePair<>(organization, userToAdd);
    }

}
