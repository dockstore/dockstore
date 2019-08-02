package io.dockstore.webservice.resources;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.helpers.CacheConfigManager;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.core.WorkflowMode.SERVICE;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/workflows")
@Api("workflows")
@Produces(MediaType.APPLICATION_JSON)
public class ServiceResource extends AbstractWorkflowResource {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceResource.class);
    private final WorkflowDAO workflowDAO;
    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final String gitHubPrivateKeyFile;
    private final String gitHubAppId;

    public ServiceResource(HttpClient client, SessionFactory sessionFactory, DockstoreWebserviceConfiguration configuration) {
        super(client, sessionFactory, configuration);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.tokenDAO = new TokenDAO(sessionFactory);
        gitHubAppId = configuration.getGitHubAppId();
        gitHubPrivateKeyFile = configuration.getGitHubAppPrivateKeyFile();
    }

    @POST
    @Path("/path/service/upsertVersion/")
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "Add or update a service version for a given GitHub tag for a service with the given repository (ex. dockstore/dockstore-ui2).", notes = "To be called by a lambda function. Error code 418 is returned to tell lambda not to retry.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Workflow upsertServiceVersion(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Repository path", required = true) @FormParam("repository") String repository,
            @ApiParam(value = "Name of user on GitHub", required = true) @FormParam("username") String username,
            @ApiParam(value = "Git reference for new GitHub tag", required = true) @FormParam("gitReference") String gitReference,
            @ApiParam(value = "GitHub installation ID", required = true) @FormParam("installationId") String installationId) {

        // Retrieve the user who triggered the call (may not exist on Dockstore)
        User sendingUser = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

        // Get Installation Access Token
        String installationAccessToken = gitHubAppSetup(installationId);

        // Call common upsert code
        String dockstoreServicePath = upsertVersionHelper(repository, gitReference, null, WorkflowMode.SERVICE, installationAccessToken);

        // Add user to service if necessary
        Workflow service = workflowDAO.findByPath(dockstoreServicePath, false, Service.class).get();
        if (sendingUser != null && !service.getUsers().contains(sendingUser)) {
            service.getUsers().add(sendingUser);
        }

        return service;
    }

    @POST
    @Path("/path/service/")
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "Create a service for the given repository (ex. dockstore/dockstore-ui2).", notes = "To be called by a lambda function. Error code 418 is returned to tell lambda not to retry.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Workflow addService(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Repository path", required = true) @FormParam("repository") String repository,
            @ApiParam(value = "Name of user on GitHub", required = true) @FormParam("username") String username,
            @ApiParam(value = "GitHub installation ID", required = true) @FormParam("installationId") String installationId) {
        // Check for duplicates (currently workflows and services share paths)
        String servicePath = "github.com/" + repository;

        // Retrieve the user who triggered the call
        User sendingUser = GitHubHelper.findUserByGitHubUsername(tokenDAO, userDAO, username, true);

        // Determine if service is already in Dockstore
        workflowDAO.findByPath(servicePath, false, Service.class).ifPresent((service) -> {
            String msg = "A service already exists for GitHub repository " + repository;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        });

        // Get Installation Access Token
        String installationAccessToken = GitHubHelper.gitHubAppSetup(gitHubAppId, gitHubPrivateKeyFile, installationId);

        // Create a service object
        final GitHubSourceCodeRepo sourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationAccessToken);

        // Check that repository exists on GitHub
        try {
            sourceCodeRepo.getRepository(repository);
        } catch (CustomWebApplicationException ex) {
            String msg = "Repository " + repository + " does not exist on GitHub";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }

        Service service = sourceCodeRepo.initializeService(repository);
        service.getUsers().add(sendingUser);
        long serviceId = workflowDAO.create(service);

        return workflowDAO.findById(serviceId);
    }

    /**
     * Does the following:
     * 1) Add user to any existing Dockstore services they should own
     *
     * 2) For all of the users organizations that have the GitHub App installed on all repositories in those organizations,
     * add any services that should be on Dockstore but are not
     *
     * 3) For all of the repositories which have the GitHub App installed, add them to Dockstore if they are missing
     * @param user
     * @param organization
     */
    void syncServicesForUser(User user, Optional<String> organization) {
        List<Token> githubByUserId = tokenDAO.findGithubByUserId(user.getId());

        if (githubByUserId.isEmpty()) {
            String msg = "The user does not have a GitHub token, please create one";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            syncServices(user, organization, githubByUserId.get(0));
        }
    }



    /**
     * Setup tokens required for GitHub apps
     * @param installationId App installation ID (per repository)
     * @return Installation access token for the given repository
     */
    private String gitHubAppSetup(String installationId) {
        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);
        String installationAccessToken = CacheConfigManager.getInstance().getInstallationAccessTokenFromCache(installationId);
        if (installationAccessToken == null) {
            String msg = "Could not get an installation access token for install with id " + installationId;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return installationAccessToken;
    }

    private void syncServices(User user, Optional<String> organization, Token gitHubToken) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(gitHubToken, client);

        // Get all GitHub repositories for the user
        final Map<String, String> workflowGitUrl2Name = gitHubSourceCodeRepo.getWorkflowGitUrl2RepositoryId();

        // Filter by organization if necessary
        final Collection<String> repositories = GitHubHelper.filterReposByOrg(workflowGitUrl2Name.values(), organization);

        // Add user to any services they should have access to that already exist on Dockstore
        final List<Workflow> existingWorkflows = findDockstoreWorkflowsForGitHubRepos(repositories);
        existingWorkflows.stream()
                .filter(workflow -> !workflow.getUsers().contains(user))
                .forEach(workflow -> workflow.getUsers().add(user));
        final Set<String> existingWorkflowPaths = existingWorkflows.stream()
                .map(workflow -> workflow.getWorkflowPath()).collect(Collectors.toSet());

        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);

        GitHubHelper.reposToCreateServicesFor(repositories, organization, existingWorkflowPaths).stream()
                .forEach(repositoryName -> {
                    final Service service = gitHubSourceCodeRepo.initializeService(repositoryName);
                    service.addUser(user);
                    final long serviceId = workflowDAO.create(service);
                    final Workflow createdService = workflowDAO.findById(serviceId);
                    final Workflow updatedService = gitHubSourceCodeRepo.getWorkflow(repositoryName, Optional.of(createdService));
                    updateDBWorkflowWithSourceControlWorkflow(createdService, updatedService);
                });
    }

    private List<Workflow> findDockstoreWorkflowsForGitHubRepos(Collection<String> repositories) {
        final List<String> workflowPaths = repositories.stream().map(repositoryName -> "github.com/" + repositoryName)
                .collect(Collectors.toList());
        return workflowDAO.findByPaths(workflowPaths, false).stream()
                .filter(workflow -> Objects.equals(workflow.getMode(), SERVICE)).collect(Collectors.toList());
    }


}
