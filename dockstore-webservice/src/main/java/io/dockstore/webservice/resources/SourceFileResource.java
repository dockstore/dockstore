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
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

@Path("/versions")
@Api("versions")
@Produces(MediaType.APPLICATION_JSON)
@SecuritySchemes({ @SecurityScheme (type = SecuritySchemeType.HTTP, name = "bearer", scheme = "bearer")})
@io.swagger.v3.oas.annotations.tags.Tag(name = "sourceFile", description = ResourceConstants.SOURCEFILE)
public class SourceFileResource {
    private static final Logger LOG = LoggerFactory.getLogger(SourceFileResource.class);

    private final SessionFactory sessionFactory;
    private final TagDAO tagDAO;
    private final WorkflowVersionDAO workflowVersionDAO;


    public SourceFileResource(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.tagDAO = new TagDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);

    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{versionId}/sourcefiles")
    @ApiOperation(value = "Retrieve sourcefiles for an entry version", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, responseContainer = "Set", response = SourceFile.class)
    public SortedSet<SourceFile> getSourceFiles(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Version to retrieve sourcefiles for", required = true) @PathParam("versionId") Long versionId,
        @ApiParam(value = "Type of entry", required = true) @QueryParam("entryType") String entryType,
        @ApiParam(value = "List of file types to filter sourcefiles by") @QueryParam("fileType") List<String> fileTypes) {

        if (entryType != null) {
            SortedSet<SourceFile> sourceFiles = new TreeSet<>();
            if ("tool".equals(entryType)) {
                Tag tag = tagDAO.findById(versionId);
                sourceFiles = tag.getSourceFiles();
            } else if ("workflow".equals(entryType)) {
                WorkflowVersion workflowVersion = workflowVersionDAO.findById(versionId);
                sourceFiles = workflowVersion.getSourceFiles();
            } else {
                throw new CustomWebApplicationException("Entry type: " + entryType + " is unsupported.", HttpStatus.SC_BAD_REQUEST);
            }
            if (fileTypes != null && !fileTypes.isEmpty()) {
                sourceFiles = sourceFiles.stream().filter(sourceFile -> {
                    if (fileTypes.contains(sourceFile.getType().toString())) {
                        return true;
                    }
                    return false;
                }).collect(Collectors.toCollection(TreeSet::new));
            }
            return sourceFiles;
        }
        throw new CustomWebApplicationException("Entry type cannot be null.", HttpStatus.SC_BAD_REQUEST);
    }

}


