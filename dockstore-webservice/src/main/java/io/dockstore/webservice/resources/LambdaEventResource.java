package io.dockstore.webservice.resources;

import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT_TEXT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_OFFSET_TEXT;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;

@Path("/lambdaEvents")
@Api("/lambdaEvents")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "lambdaEvents", description = ResourceConstants.LAMBDAEVENTS)
public class LambdaEventResource {
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
    public List<LambdaEvent> getLambdaEventsByOrganization(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "organization", required = true) @PathParam("organization") String organization,
            @ApiParam(value = PAGINATION_OFFSET_TEXT) @QueryParam("offset") @DefaultValue("0") String offset,
            @ApiParam(value = PAGINATION_LIMIT_TEXT, allowableValues = "range[1,100]", defaultValue = PAGINATION_LIMIT) @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") Integer limit) {
        final User authUser = userDAO.findById(user.getId());
        final List<Token> githubTokens = tokenDAO.findGithubByUserId(authUser.getId());
        if (githubTokens.isEmpty()) {
            throw new CustomWebApplicationException("You do not have GitHub connected to your account.", HttpStatus.SC_BAD_REQUEST);
        }
        final Token githubToken = githubTokens.get(0);
        final Optional<List<String>> authorizedRepos = authorizedRepos(organization, githubToken);
        return lambdaEventDAO.findByOrganization(organization, offset, limit, authorizedRepos);
    }

    /**
     * Returns an Optional list of the repositories in the organization the user has access to. If
     * the user is an organziation member hand has access to all repositories in the organization,
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

