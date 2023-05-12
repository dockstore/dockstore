package io.dockstore.webservice.resources;

import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.CloudInstance;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.CloudInstanceDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;

@Path("/cloudInstances")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cloud Instances")
public class CloudInstanceResource implements AuthenticatedResourceInterface {
    private CloudInstanceDAO cloudInstanceDAO;

    public CloudInstanceResource(SessionFactory sessionFactory) {
        this.cloudInstanceDAO = new CloudInstanceDAO(sessionFactory);

    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getCloudInstances", summary = "Get all known public cloud instances")
    @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CloudInstance.class))))
    public List<CloudInstance> getCloudInstances() {
        List<CloudInstance> allWithoutUser = this.cloudInstanceDAO.findAllWithoutUser();
        allWithoutUser.forEach(e -> Hibernate.initialize(e.getSupportedLanguages()));
        return allWithoutUser;
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{cloudInstanceId}")
    @RolesAllowed({ "admin" })
    @Operation(operationId = "deleteCloudInstance", summary = "Delete a public cloud instance, admin only", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "No Content")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "Forbidden")
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = "Unauthorized")
    public void deleteCloudInstance(@Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(description = "ID of cloud instance to delete", name = "cloudInstanceId", in = ParameterIn.PATH, required = true) @PathParam("cloudInstanceId") Long cloudInstanceId) {
        this.cloudInstanceDAO.deleteById(cloudInstanceId);
    }

    @POST
    @Timed
    @UnitOfWork
    @RolesAllowed({ "admin" })
    @Operation(operationId = "postCloudInstance", summary = "Add a new public cloud instance, admin only", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "No Content")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "Forbidden")
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = "Unauthorized")
    public void postCloudInstance(@Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(name = "Cloud Instance", description = "Cloud instance to create", required = true) CloudInstance cloudInstance) {
        CloudInstance cloudInstanceToBeAdded = new CloudInstance();
        cloudInstanceToBeAdded.setPartner(cloudInstance.getPartner());
        cloudInstanceToBeAdded.setUrl(cloudInstance.getUrl());
        cloudInstanceToBeAdded.setSupportsFileImports(cloudInstance.isSupportsFileImports());
        cloudInstanceToBeAdded.setSupportsHttpImports(cloudInstance.isSupportsHttpImports());
        cloudInstanceToBeAdded.setSupportedLanguages(cloudInstance.getSupportedLanguages());
        cloudInstanceToBeAdded.setDisplayName(cloudInstance.getDisplayName());
        cloudInstanceToBeAdded.setUser(null);
        this.cloudInstanceDAO.create(cloudInstanceToBeAdded);
    }
}
