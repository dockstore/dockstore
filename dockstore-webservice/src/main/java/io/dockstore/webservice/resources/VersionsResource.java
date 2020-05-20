package io.dockstore.webservice.resources;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.OPENAPI_JWT_SECURITY_DEFINITION_NAME;

@Path("/versions")
@Api("versions")
@Produces(MediaType.APPLICATION_JSON)
@SecuritySchemes({ @SecurityScheme (type = SecuritySchemeType.HTTP, name = "bearer", scheme = JWT_SECURITY_DEFINITION_NAME)})
@io.swagger.v3.oas.annotations.tags.Tag(name = "sourceFile", description = ResourceConstants.SOURCEFILE)
public class VersionsResource {
    private static final Logger LOG = LoggerFactory.getLogger(VersionsResource.class);

    private final SessionFactory sessionFactory;
    private final TagDAO tagDAO;
    private final WorkflowVersionDAO workflowVersionDAO;


    public VersionsResource(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.tagDAO = new TagDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{versionId}/sourcefiles")
    @Operation(operationId = "getVersionsSourcefiles", description = "Retrieve sourcefiles for an entry's version", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    public SortedSet<SourceFile> getSourceFiles(@ApiParam(hidden = true) @Auth User user,
            @Parameter(name = "versionId", description = "Version to retrieve sourcefiles for", required = true, in = ParameterIn.PATH) @PathParam("versionId") Long versionId,
            @Parameter(name = "entryType", description = "Type of entry", required = true, in = ParameterIn.QUERY) @QueryParam("entryType") String entryType,
            @Parameter(name = "fileTypes", description = "List of file types to filter sourcefiles by") @QueryParam("fileType") List<String> fileTypes) {
        if (entryType != null) {
            SortedSet<SourceFile> sourceFiles = new TreeSet<>();
            Version version;
            if ("tool".equals(entryType)) {
                version = tagDAO.findById(versionId);
            } else if ("workflow".equals(entryType)) {
                version = workflowVersionDAO.findById(versionId);
            } else {
                throw new CustomWebApplicationException("Entry type: " + entryType + " is unsupported.", HttpStatus.SC_BAD_REQUEST);
            }
            sourceFiles = version.getSourceFiles();
            if (fileTypes != null && !fileTypes.isEmpty()) {
                sourceFiles = sourceFiles.stream().filter(sourceFile -> fileTypes.contains(sourceFile.getType().toString())).collect(Collectors.toCollection(TreeSet::new));
            }
            return sourceFiles;
        }
        throw new CustomWebApplicationException("Entry type cannot be null.", HttpStatus.SC_BAD_REQUEST);
    }

}


