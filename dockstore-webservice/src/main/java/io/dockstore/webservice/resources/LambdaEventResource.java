package io.dockstore.webservice.resources;

import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.jdbi.LambdaEventDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;

@Path("/lambdaEvents")
@Api("/lambdaEvents")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "lambdaEvents", description = ResourceConstants.LAMBDAEVENTS)
public class LambdaEventResource {
    public static final String X_TOTAL_COUNT = "X-total-count";
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private final LambdaEventDAO lambdaEventDAO;
    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;

    public LambdaEventResource(SessionFactory sessionFactory) {
        this.lambdaEventDAO = new LambdaEventDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.tokenDAO = new TokenDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{organization}")
    @Operation(operationId = "getLambdaEventsByOrganization", description = "Get all of the Lambda Events for the given GitHub organization.", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details")
    @SuppressWarnings("checkstyle:parameternumber")
    public List<LambdaEvent> getLambdaEventsByOrganization(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @PathParam("organization") String organization,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") Integer limit,
            @DefaultValue("") @QueryParam("filter") String filter,
            @DefaultValue("dbCreateDate") @QueryParam("sortCol") String sortCol,
            @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
            @Context HttpServletResponse response) {
        final User authUser = userDAO.findById(user.getId());
        final List<Token> githubTokens = tokenDAO.findGithubByUserId(authUser.getId());
        if (githubTokens.isEmpty()) {
            throw new CustomWebApplicationException("You do not have GitHub connected to your account.", HttpStatus.SC_BAD_REQUEST);
        }
        final Token githubToken = githubTokens.get(0);
        final Optional<List<String>> authorizedRepos = authorizedRepos(organization, githubToken);
        response.addHeader(X_TOTAL_COUNT, String.valueOf(lambdaEventDAO.countByOrganization(organization, authorizedRepos, filter)));
        response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, X_TOTAL_COUNT);
        return lambdaEventDAO.findByOrganization(organization, offset, limit, filter, sortCol, sortOrder, authorizedRepos);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @RolesAllowed({ "admin", "curator"})
    @Path("/user/{userid}")
    @Operation(operationId = "getUserLambdaEvents", description = "Get all of the Lambda Events for the given user.",
            security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @SuppressWarnings("checkstyle:parameternumber")
    public List<LambdaEvent> getUserLambdaEvents(@Parameter(hidden = true, name = "user")@Auth User authUser,
           @PathParam("userid") long userid,
           @QueryParam("offset") @DefaultValue("0") Integer offset,
           @QueryParam("limit") @DefaultValue("1000") Integer limit,
           @DefaultValue("") @QueryParam("filter") String filter,
           @DefaultValue("dbCreateDate") @QueryParam("sortCol") String sortCol,
           @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
           @Context HttpServletResponse response) {
        final User user = userDAO.findById(userid);
        if (user == null) {
            throw new CustomWebApplicationException("User not found.", HttpStatus.SC_NOT_FOUND);
        }
        response.addHeader(LambdaEventResource.X_TOTAL_COUNT, String.valueOf(lambdaEventDAO.countByUser(user, filter)));
        response.addHeader(LambdaEventResource.ACCESS_CONTROL_EXPOSE_HEADERS, LambdaEventResource.X_TOTAL_COUNT);
        return lambdaEventDAO.findByUser(user, offset, limit, filter, sortCol, sortOrder);
    }

    /**
     * Returns an Optional list of the repositories in the organization the user has access to. If
     * the user is an organization member and has access to all repositories in the organization,
     * returns an <code>Optional.empty()</code>.
     * If the user has no access to the organization or any of its repos, throws a 401 CustomWebApplicationException.
     *
     * @param organization
     * @param gitHubToken
     * @return
     */
    private Optional<List<String>> authorizedRepos(String organization, Token gitHubToken) {
        final GitHubSourceCodeRepo sourceCodeRepoInterface = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(gitHubToken);
        if (!sourceCodeRepoInterface.isOneOfMyOrganizations(organization)) {
            final List<String> gitHubOrgRepos = organizationRepositories(organization, sourceCodeRepoInterface);
            if (gitHubOrgRepos.isEmpty()) {
                throw new CustomWebApplicationException(
                    "You do not have access to the GitHub organization '" + organization + "'",
                    HttpStatus.SC_UNAUTHORIZED);
            }
            return Optional.of(gitHubOrgRepos);
        }
        return Optional.empty();
    }

    /**
     * Returns a list of repository names, e.g, "dockstore-ui2" for org/repo of "dockstore/dockstore-ui2"
     * that the user has been granted specific access to in the <code>organization</code>
     * @param organization
     * @param sourceCodeRepoInterface
     * @return
     */
    private List<String> organizationRepositories(String organization, GitHubSourceCodeRepo sourceCodeRepoInterface) {
        final Map<String, String> repositoriesWithMemberAccess =
            sourceCodeRepoInterface.getRepositoriesWithMemberAccess();
        // Example values are org/repo, e.g., "dockstore/dockstore", "dockstore/dockstore-ui2", etc.
        final List<String> gitHubOrgRepos = repositoriesWithMemberAccess.values().stream()
            .filter(fullname -> fullname.startsWith(organization + "/"))
            .map(fullname -> fullname.split("/")[1])
            .collect(Collectors.toList());
        return gitHubOrgRepos;
    }

}

