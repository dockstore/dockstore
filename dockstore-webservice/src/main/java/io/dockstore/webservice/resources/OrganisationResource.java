package io.dockstore.webservice.resources;

import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Organisation;
import io.dockstore.webservice.core.OrganisationUser;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.jdbi.OrganisationDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * Collection of organisation endpoints
 * @author aduncan
 */
@Path("/organisations")
@Api("/organisations")
@Produces(MediaType.APPLICATION_JSON)
public class OrganisationResource implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(OrganisationResource.class);

    private final OrganisationDAO organisationDAO;
    private final ElasticManager elasticManager;
    private final SessionFactory sessionFactory;

    public OrganisationResource(SessionFactory sessionFactory) {
        this.organisationDAO = new OrganisationDAO(sessionFactory);
        this.sessionFactory = sessionFactory;
        elasticManager = new ElasticManager();
    }

    @PUT
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @Path("/approve/{organisationId}")
    @ApiOperation(value = "Approves an organisation.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin only", response = Organisation.class)
    public Organisation approveOrganisation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long id) {
        Organisation organisation = organisationDAO.findById(id);
        organisation.setApproved(true);
        return organisationDAO.findById(id);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/approved")
    @ApiOperation(value = "List all available organisations.", notes = "NO Authentication", responseContainer = "List", response = Organisation.class)
    public List<Organisation> getPublishedOrganisations() {
        return organisationDAO.findAllApproved();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{organisationId}")
    @ApiOperation(value = "Retrieves an organisation by ID.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organisation.class)
    public Organisation getOrganisationById(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation ID.", required = true) @PathParam("organisationId") Long id) {
        Organisation organisation = organisationDAO.findById(id);
        if (organisation == null) {
            throw new CustomWebApplicationException("Organisation not found", HttpStatus.SC_BAD_REQUEST);
        }
        return organisation;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/all")
    @RolesAllowed("admin")
    @ApiOperation(value = "List all organisations.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Admin only", responseContainer = "List", response = Organisation.class)
    public List<Organisation> getAllOrganisations() {
        return organisationDAO.findAll();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/create")
    @ApiOperation(value = "Create an organisation", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Organisation.class)
    public Organisation createOrganisation(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Organisation to register.", required = true) Organisation organisation) {

        // Check if any other organisations exist with that name
        Organisation matchingOrg = organisationDAO.findByName(organisation.getName());
        if (matchingOrg != null) {
            String msg = "An organisation already exists with either the name '" + organisation.getName() + "'.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        // Save organisation
        long id = organisationDAO.create(organisation);

        // Create Role for user creating the organisation
        OrganisationUser organisationUser = new OrganisationUser(user, organisationDAO.findById(id), OrganisationUser.Role.MAINTAINER);
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.persist(organisationUser);

        return organisationDAO.findById(id);
    }
}
