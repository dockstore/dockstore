/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.AMAZON_ECR_PRIVATE_REGISTRY_REGEX;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.MAX_PAGINATION_LIMIT;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.Registry;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.api.PublishRequest;
import io.dockstore.webservice.api.StarRequest;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Entry.TopicSelection;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.AbstractImageRegistry;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.ImageRegistryFactory;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.QuayImageRegistry;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.StringInputValidationHelper;
import io.dockstore.webservice.helpers.TopicHarvester;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.quay.client.model.QuayRepo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/containers")
@Api("containers")
@Produces(MediaType.APPLICATION_JSON)
@io.swagger.v3.oas.annotations.tags.Tag(name = "containers", description = ResourceConstants.CONTAINERS)
public class DockerRepoResource
    implements AuthenticatedResourceInterface, EntryVersionHelper<Tool, Tag, ToolDAO>, StarrableResourceInterface,
    SourceControlResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoResource.class);
    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for published tools, authentication can be provided for restricted tools";
    public static final String UNABLE_TO_VERIFY_THAT_YOUR_TOOL_POINTS_AT_A_VALID_SOURCE_CONTROL_REPO = "unable to verify that your tool points at a valid source control repo";
    private static final String GET_A_PUBLISHED_TOOL_DESC = "Get a published tool.";

    @Context
    private ResourceContext rc;

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;
    private final LabelDAO labelDAO;
    private final FileDAO fileDAO;
    private final FileFormatDAO fileFormatDAO;
    private final VersionDAO versionDAO;
    private final HttpClient client;
    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final String dashboardPrefix;
    private final EventDAO eventDAO;
    private final WorkflowResource workflowResource;
    private final EntryResource entryResource;
    private final SessionFactory sessionFactory;

    public DockerRepoResource(final HttpClient client, final SessionFactory sessionFactory, final DockstoreWebserviceConfiguration configuration,
        final WorkflowResource workflowResource, final EntryResource entryResource) {

        this.sessionFactory = sessionFactory;
        this.userDAO = new UserDAO(sessionFactory);
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.tagDAO = new TagDAO(sessionFactory);
        this.labelDAO = new LabelDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.fileFormatDAO = new FileFormatDAO(sessionFactory);
        this.versionDAO = new VersionDAO(sessionFactory);
        this.client = client;

        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
        this.dashboardPrefix = configuration.getDashboard();

        this.workflowResource = workflowResource;
        this.entryResource = entryResource;

        this.toolDAO = new ToolDAO(sessionFactory);
    }

    void refreshToolsForUser(Long userId, String organization, String repository) {
        refreshBitbucketToken(userId);

        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO);
        Token githubToken = Token.extractToken(tokens, TokenType.GITHUB_COM);
        Token bitbucketToken = Token.extractToken(tokens, TokenType.BITBUCKET_ORG);
        Token gitlabToken = Token.extractToken(tokens, TokenType.GITLAB_COM);

        // with Docker Hub support it is now possible that there is no quayToken
        checkTokens(quayToken, githubToken, bitbucketToken, gitlabToken);

        // Get a list of all namespaces from Quay.io only
        if (quayToken == null) {
            throw new CustomWebApplicationException("Missing required Quay.io token", HttpStatus.SC_BAD_REQUEST);
        }
        QuayImageRegistry registry = new QuayImageRegistry(quayToken);
        registry.refreshTool(userId, userDAO, toolDAO, tagDAO, fileDAO, fileFormatDAO, githubToken, bitbucketToken,
            gitlabToken, organization, eventDAO, dashboardPrefix, repository);
    }

    private void refreshBitbucketToken(long userId) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(userId);
        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }
    }

    private static void checkTokens(final Token quayToken, final Token githubToken, final Token bitbucketToken, final Token gitlabToken) {
        if (githubToken == null) {
            LOG.info("GIT token not found!");
            throw new CustomWebApplicationException("Git token not found.", HttpStatus.SC_CONFLICT);
        }
        if (bitbucketToken == null) {
            LOG.info("WARNING: BITBUCKET token not found!");
        }
        if (gitlabToken == null) {
            LOG.info("WARNING: GITLAB token not found!");
        }
        if (quayToken == null) {
            LOG.info("WARNING: QUAY token not found!");
        }
    }

    @GET
    @Path("/{containerId}/refresh")
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Operation(operationId = "refresh", description = "Refresh one particular tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Refresh one particular tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tool.class)
    public Tool refresh(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        checkCanWrite(user, tool);
        checkNotHosted(tool);
        // Update user data
        User dbUser = userDAO.findById(user.getId());
        dbUser.updateUserMetadata(tokenDAO);

        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }

        Tool refreshedTool = refreshContainer(containerId, user.getId());

        // Refresh checker workflow
        if (refreshedTool.getCheckerWorkflow() != null) {
            workflowResource.refresh(user, refreshedTool.getCheckerWorkflow().getId(), true);
        }
        refreshedTool.getWorkflowVersions().forEach(Version::updateVerified);
        PublicStateManager.getInstance().handleIndexUpdate(refreshedTool, StateManagerMode.UPDATE);
        EntryVersionHelper.removeSourceFilesFromEntry(tool, sessionFactory);
        return refreshedTool;
    }

    private Tool refreshContainer(final long containerId, final long userId) {
        Tool tool = toolDAO.findById(containerId);

        // Check if tool has a valid Git URL (needed to refresh!)
        String gitUrl = tool.getGitUrl();
        Optional<Map<String, String>> gitMap = SourceCodeRepoFactory.parseGitUrl(gitUrl);

        if (gitMap.isEmpty()) {
            LOG.error("Could not parse Git URL:" + gitUrl + " Unable to refresh tool!");
            throw new CustomWebApplicationException("Could not parse Git URL:" + gitUrl + " Unable to refresh tool!",
                HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO);
        Token githubToken = Token.extractToken(tokens, TokenType.GITHUB_COM);
        Token gitlabToken = Token.extractToken(tokens, TokenType.GITLAB_COM);
        Token bitbucketToken = Token.extractToken(tokens, TokenType.BITBUCKET_ORG);

        // with Docker Hub support it is now possible that there is no quayToken
        checkTokens(quayToken, githubToken, bitbucketToken, gitlabToken);

        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
            .createSourceCodeRepo(tool.getGitUrl(), bitbucketToken == null ? null : bitbucketToken.getContent(),
                gitlabToken == null ? null : gitlabToken.getContent(), githubToken);

        // Get all registries
        ImageRegistryFactory factory = new ImageRegistryFactory(quayToken);
        final AbstractImageRegistry abstractImageRegistry = factory.createImageRegistry(tool.getRegistryProvider());

        if (abstractImageRegistry == null) {
            throw new CustomWebApplicationException("unable to establish connection to registry, check that you have linked your accounts",
                HttpStatus.SC_NOT_FOUND);
        }
        return abstractImageRegistry.refreshTool(containerId, userId, userDAO, toolDAO, tagDAO, fileDAO, fileFormatDAO, githubToken, sourceCodeRepo, eventDAO, dashboardPrefix);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}")
    @Operation(operationId = "getContainer", description = "Retrieve a tool", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Retrieve a tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tool.class, notes = "This is one of the few endpoints that returns the user object with populated properties (minus the userProfiles property)")
    public Tool getContainer(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "Comma-delimited list of fields to include: validations") @QueryParam("include") String include) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        checkCanExamine(user, tool);

        if (checkIncludes(include, "validations")) {
            tool.getWorkflowVersions().forEach(tag -> Hibernate.initialize(tag.getValidations()));
        }
        tool.getWorkflowVersions().forEach(tag -> Hibernate.initialize(tag.getImages()));
        Hibernate.initialize(tool.getAliases());
        return tool;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/labels")
    @Operation(operationId = "updateLabels", description = "Update the labels linked to a tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Update the labels linked to a tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Tool.class)
    public Tool updateLabels(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "Comma-delimited list of labels.", required = true) @QueryParam("labels") String labelStrings,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        return this.updateLabels(user, containerId, labelStrings, labelDAO);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateContainer", summary = "Update the tool with the given tool.",
        description = "Updates default descriptor paths, default Dockerfile paths, default test parameter paths, git url,"
            + " and default version. Also updates tool maintainer email, and private access for manual tools.",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Update the tool with the given tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tool.class,
        notes = "Updates default descriptor paths, default Dockerfile paths, default test parameter paths, git url,"
            + " and default version. Also updates tool maintainer email, and private access for manual tools.")
    public Tool updateContainer(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "Tool with updated information", required = true) Tool tool) {
        Tool foundTool = toolDAO.findById(containerId);
        checkNotNullEntry(foundTool);
        checkCanWrite(user, foundTool);

        // Don't need to check for duplicate tool because the tool path can't be updated

        Registry registry = foundTool.getRegistryProvider();
        if (registry.isPrivateOnly() && !tool.isPrivateAccess()) {
            throw new CustomWebApplicationException("The registry " + registry.getFriendlyName() + " is private only, cannot set tool to public.", HttpStatus.SC_BAD_REQUEST);
        }

        if (registry.isPrivateOnly() && Strings.isNullOrEmpty(tool.getToolMaintainerEmail())) {
            throw new CustomWebApplicationException("Private tools require a tool maintainer email.", HttpStatus.SC_BAD_REQUEST);
        }

        List<String> authorEmails = foundTool.getAuthors().stream().map(Author::getEmail).filter(Objects::nonNull).toList();
        if (!foundTool.isPrivateAccess() && tool.isPrivateAccess() && Strings.isNullOrEmpty(tool.getToolMaintainerEmail()) && authorEmails.isEmpty()) {
            throw new CustomWebApplicationException("A published, private tool must have either an tool author email or tool maintainer email set up.", HttpStatus.SC_BAD_REQUEST);
        }

        // An Amazon ECR repository cannot change its visibility once it's created. Thus, Amazon ECR tools cannot have their visibility changed.
        if (registry == Registry.AMAZON_ECR) {
            checkAmazonECRPrivateAccess(foundTool.getRegistry(), tool.isPrivateAccess());
        }

        updateInfo(foundTool, tool);

        Tool result = toolDAO.findById(containerId);
        checkNotNullEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result;

    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{toolId}/defaultVersion")
    @Operation(operationId = "updateDefaultVersion", description = "Update the default version of the given tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Update the default version of the given tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tool.class, nickname = "updateToolDefaultVersion")
    public Tool updateDefaultVersion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("toolId") Long toolId,
        @ApiParam(value = "Tag name to set as default.", required = true) String version) {
        return (Tool) updateDefaultVersionHelper(version, toolId, user);
    }

    /**
     * Updates information from given tool based on the new tool
     *
     * @param originalTool the original tool from the database
     * @param newTool      the new tool from the webservice
     */
    private void updateInfo(Tool originalTool, Tool newTool) {
        //TODO this could probably be better handled better, maybe with some Jackson magic?
        if (!Objects.equals(originalTool.getMode(), newTool.getMode())) {
            throw new CustomWebApplicationException("You cannot change the mode of a tool.", HttpStatus.SC_BAD_REQUEST);
        }

        // Add descriptor type default paths here
        // ignore path changes for hosted workflows
        if (!Objects.equals(originalTool.getMode(), ToolMode.HOSTED)) {
            originalTool.setDefaultCwlPath(newTool.getDefaultCwlPath());
            originalTool.setDefaultWdlPath(newTool.getDefaultWdlPath());
            originalTool.setDefaultDockerfilePath(newTool.getDefaultDockerfilePath());
            originalTool.setDefaultTestCwlParameterFile(newTool.getDefaultTestCwlParameterFile());
            originalTool.setDefaultTestWdlParameterFile(newTool.getDefaultTestWdlParameterFile());
            originalTool.setGitUrl(newTool.getGitUrl());
        }

        if (newTool.getDefaultVersion() != null) {
            if (!originalTool.checkAndSetDefaultVersion(newTool.getDefaultVersion())) {
                throw new CustomWebApplicationException("Tool version does not exist.", HttpStatus.SC_BAD_REQUEST);
            }
        }

        originalTool.setForumUrl(newTool.getForumUrl());
        // Only manual topics can be updated by users. Automatic and AI topics are not submitted by users
        originalTool.setTopicManual(newTool.getTopicManual());
        // Update topic selection if it's a non-hosted tool, or if it's a hosted tool and the new topic selection is not automatic.
        // Hosted tools don't have a source control thus cannot have an automatic topic.
        if (!Objects.equals(originalTool.getMode(), ToolMode.HOSTED) || (Objects.equals(originalTool.getMode(), ToolMode.HOSTED) && newTool.getTopicSelection() != TopicSelection.AUTOMATIC)) {
            originalTool.setTopicSelection(newTool.getTopicSelection());
        }

        originalTool.setApprovedAITopic(newTool.isApprovedAITopic());

        if (originalTool.getMode() == ToolMode.MANUAL_IMAGE_PATH) {
            originalTool.setToolMaintainerEmail(newTool.getToolMaintainerEmail());
            originalTool.setPrivateAccess(newTool.isPrivateAccess());
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/updateTagPaths")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateTagContainerPath", description = "Change the tool paths.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Change the tool paths.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Resets the descriptor paths and dockerfile path of all versions to match the default paths from the tool object passed.", response = Tool.class)
    public Tool updateTagContainerPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "Tool with updated information", required = true) Tool tool) {

        Tool foundTool = toolDAO.findById(containerId);

        //use helper to check the user and the entry
        checkNotNullEntry(foundTool);
        checkNotHosted(foundTool);
        checkCanWrite(user, foundTool);

        //update the tool path in all workflowVersions
        Set<Tag> tags = foundTool.getWorkflowVersions();
        for (Tag tag : tags) {
            if (!tag.isDirtyBit()) {
                tag.setCwlPath(tool.getDefaultCwlPath());
                tag.setWdlPath(tool.getDefaultWdlPath());
                tag.setDockerfilePath(tool.getDefaultDockerfilePath());
            }
        }
        PublicStateManager.getInstance().handleIndexUpdate(foundTool, StateManagerMode.UPDATE);
        return toolDAO.findById(containerId);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}/users")
    @Operation(operationId = "getUsers", description = "Get users of a tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get users of a tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        checkCanExamine(user, tool);
        return new ArrayList<>(tool.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/published/{containerId}")
    @Operation(operationId = "getPublishedContainer", description = GET_A_PUBLISHED_TOOL_DESC)
    @ApiOperation(value = GET_A_PUBLISHED_TOOL_DESC, notes = "NO authentication", response = Tool.class)
    public Tool getPublishedContainer(@ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "Comma-delimited list of fields to include: validations") @QueryParam("include") String include) {
        Tool tool = toolDAO.findPublishedById(containerId);
        checkNotNullEntry(tool);

        if (checkIncludes(include, "validations")) {
            tool.getWorkflowVersions().forEach(tag -> Hibernate.initialize(tag.getValidations()));
        }
        Hibernate.initialize(tool.getAliases());
        return filterContainersForHiddenTags(tool);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/namespace/{namespace}/published")
    @Operation(operationId = "getPublishedContainersByNamespace", description = "List all published tools belonging to the specified namespace.")
    @ApiOperation(value = "List all published tools belonging to the specified namespace.", notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> getPublishedContainersByNamespace(
        @ApiParam(value = "namespace", required = true) @PathParam("namespace") String namespace) {
        List<Tool> tools = toolDAO.findPublishedByNamespace(namespace);
        filterContainersForHiddenTags(tools);
        return tools;
    }

    @POST
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Path("/registerManual")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "registerManual", description = "Register a tool manually, along with tags.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Register a tool manually, along with tags.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tool.class)
    public Tool registerManual(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to be registered", required = true) Tool toolParam) {
        // Check for custom docker registries
        Registry registry = toolParam.getRegistryProvider();
        if (registry == null) {
            throw new CustomWebApplicationException("The provided registry is not valid. If you are using a custom registry please ensure that it matches the allowed paths.",
                HttpStatus.SC_BAD_REQUEST);
        }
        Tool duplicate = toolDAO.findByPath(toolParam.getToolPath(), false);

        if (duplicate != null) {
            LOG.info(user.getUsername() + ": duplicate tool found: {}" + toolParam.getToolPath());
            throw new CustomWebApplicationException("Tool " + toolParam.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if tool has tags
        if (toolParam.getRegistry().equals(Registry.QUAY_IO.getDockerPath()) && !checkContainerForTags(toolParam, user.getId())) {
            LOG.info(user.getUsername() + ": tool has no tags.");
            throw new CustomWebApplicationException(
                "Tool " + toolParam.getToolPath() + " has no tags. Quay containers must have at least one tag.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if user owns repo, or if user is in the organization which owns the tool
        if (toolParam.getRegistry().equals(Registry.QUAY_IO.getDockerPath()) && !checkIfUserOwns(toolParam, user.getId())) {
            LOG.info(user.getUsername() + ": User does not own the given Quay Repo.");
            throw new CustomWebApplicationException("User does not own the tool " + toolParam.getPath()
                + ". You can only add Quay repositories that you own or are part of the organization", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if the tool has a valid tool name
        StringInputValidationHelper.checkEntryName(toolParam.getClass(), toolParam.getToolname());

        // check that the repo is valid
        if (!isGit(toolParam.getGitUrl())) {
            toolParam.setGitUrl(convertHttpsToSsh(toolParam.getGitUrl()));
        }
        this.checkRepoValidity(user, toolParam);

        final Set<Tag> workflowVersionsFromParam = Sets.newHashSet(toolParam.getWorkflowVersions());
        toolParam.setWorkflowVersions(Sets.newHashSet());
        // cannot create tool with a transient version hanging on it
        long id = toolDAO.create(toolParam);
        Tool tool = toolDAO.findById(id);
        // put the hanging versions back
        toolParam.setWorkflowVersions(workflowVersionsFromParam);

        if (registry.isPrivateOnly() && !tool.isPrivateAccess()) {
            throw new CustomWebApplicationException("The registry " + registry.getFriendlyName() + " is a private only registry.", HttpStatus.SC_BAD_REQUEST);
        }

        if (tool.isPrivateAccess() && Strings.isNullOrEmpty(tool.getToolMaintainerEmail())) {
            throw new CustomWebApplicationException("Tool maintainer email is required for private tools.", HttpStatus.SC_BAD_REQUEST);
        }

        if (registry == Registry.AMAZON_ECR) {
            checkAmazonECRPrivateAccess(tool.getRegistry(), tool.isPrivateAccess());
        }

        // populate user in tool
        tool.addUser(user);
        // create dependent Tags before creating tool
        Set<Tag> createdTags = new HashSet<>();
        for (Tag tag : toolParam.getWorkflowVersions()) {
            tag.setParent(tool);
            final long l = tagDAO.create(tag);
            Tag byId = tagDAO.findById(l);
            createdTags.add(byId);
            this.eventDAO.createAddTagToEntryEvent(Optional.of(user), tool, byId);
        }
        tool.getWorkflowVersions().clear();
        tool.getWorkflowVersions().addAll(createdTags);
        // create dependent Labels before creating tool
        Set<Label> createdLabels = new HashSet<>();
        for (Label label : tool.getLabels()) {
            final long l = labelDAO.create(label);
            createdLabels.add(labelDAO.findById(l));
        }
        tool.getLabels().clear();
        tool.getLabels().addAll(createdLabels);

        // Can't set tool license information here, far too many tests register a tool without a GitHub token
        setToolLicenseInformation(user, tool);

        // Set the automatic topic
        new TopicHarvester(user, tokenDAO).harvestAndSetTopic(tool);

        return toolDAO.findById(id);
    }

    /**
     * Set the license information for a tool
     *
     * @param user The user the tool belongs to
     * @param tool The tool to get license information for
     */
    private void setToolLicenseInformation(User user, Tool tool) {
        List<Token> tokens = tokenDAO.findByUserId(user.getId());
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(tool.getGitUrl(), tokens);

        if (sourceCodeRepo != null) {
            sourceCodeRepo.checkSourceControlTokenValidity();
            String gitRepositoryFromGitUrl = AbstractImageRegistry.getGitRepositoryFromGitUrl(tool.getGitUrl());
            sourceCodeRepo.setLicenseInformation(tool, gitRepositoryFromGitUrl);
        }
    }

    /**
     * Check whether a tool is pointing at a valid repo that the user has access to.
     * This is a bit silly because it looks like the methods in SourceCodeRepoInterface
     * mainly check whether the github token is valid, not whether the repo exists
     *
     * @param user The user the tool belongs to
     * @param tool The tool to get license information for
     */
    private void checkRepoValidity(User user, Tool tool) {
        refreshBitbucketToken(user.getId());
        List<Token> tokens = tokenDAO.findByUserId(user.getId());
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(tool.getGitUrl(), tokens);
        if (sourceCodeRepo == null || !sourceCodeRepo.checkSourceControlRepoValidity(tool)) {
            throw new CustomWebApplicationException(UNABLE_TO_VERIFY_THAT_YOUR_TOOL_POINTS_AT_A_VALID_SOURCE_CONTROL_REPO,
                HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Look for the tags that a tool has using a user's own tokens
     *
     * @param tool   the tool to examine
     * @param userId the id for the user that is doing the checking
     * @return true if the container has tags
     */
    private boolean checkContainerForTags(final Tool tool, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO);
        if (quayToken == null) {
            // no quay token extracted
            throw new CustomWebApplicationException("no quay token found, please link your quay.io account to read from quay.io",
                HttpStatus.SC_NOT_FOUND);
        }
        ImageRegistryFactory factory = new ImageRegistryFactory(quayToken);

        final AbstractImageRegistry imageRegistry = factory.createImageRegistry(tool.getRegistryProvider());
        final List<Tag> tags = imageRegistry.getTags(tool);

        return !tags.isEmpty();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @Operation(operationId = "deleteContainer", description = "Delete a tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Delete a tool.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid "))
    public Response deleteContainer(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool id to delete", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkCanWrite(user, tool);
        Tool deleteTool = new Tool();
        deleteTool.setId(tool.getId());
        deleteTool.setActualDefaultVersion(null);
        eventDAO.deleteEventByEntryID(tool.getId());
        toolDAO.delete(tool);
        tool.getWorkflowVersions().clear();
        tool = toolDAO.findById(containerId);
        if (tool == null) {
            PublicStateManager.getInstance().handleIndexUpdate(deleteTool, StateManagerMode.DELETE);
            return Response.noContent().build();
        } else {
            return Response.serverError().build();
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "publish", description = "Publish or unpublish a tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Publish or unpublish a tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tool.class)
    public Tool publish(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool id to publish", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) PublishRequest request) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        if (!isAdmin(user)) {
            checkCanShare(user, tool);
        }
        checkNotArchived(tool);

        if (tool.getIsPublished() == request.getPublish()) {
            return tool;
        }

        Workflow checker = tool.getCheckerWorkflow();

        final Optional<User> foundUser = Optional.of(userDAO.findById(user.getId()));
        if (request.getPublish()) {
            if (tool.isPrivateAccess()) {
                // Check that either tool maintainer email or author email is not null
                List<String> authorEmails = tool.getAuthors().stream().map(Author::getEmail).filter(Objects::nonNull).toList();
                if (Strings.isNullOrEmpty(tool.getToolMaintainerEmail()) && authorEmails.isEmpty()) {
                    throw new CustomWebApplicationException(
                        "Either a tool email or tool maintainer email is required to publish private tools.", HttpStatus.SC_BAD_REQUEST);
                }
            }

            // Can publish a tool IF it has at least one valid tag (or is manual) and a git url
            final boolean validTag = tool.getWorkflowVersions().stream().anyMatch(Version::isValid);
            if (validTag && (!tool.getGitUrl().isEmpty() || Objects.equals(tool.getMode(), ToolMode.HOSTED))) {
                tool.setIsPublished(true);
                if (checker != null) {
                    if (!checker.getIsPublished()) {
                        eventDAO.publishEvent(true, foundUser, checker);
                    }
                    checker.setIsPublished(true);
                }
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            tool.setIsPublished(false);
            if (checker != null) {
                if (checker.getIsPublished()) {
                    eventDAO.publishEvent(false, foundUser, checker);
                }
                checker.setIsPublished(false);
            }
        }

        long id = toolDAO.create(tool);
        tool = toolDAO.findById(id);
        if (request.getPublish()) {
            PublicStateManager.getInstance().handleIndexUpdate(tool, StateManagerMode.PUBLISH);
            if (tool.getTopicId() == null) {
                try {
                    entryResource.createAndSetDiscourseTopic(id);
                } catch (CustomWebApplicationException ex) {
                    LOG.error("Error adding discourse topic.", ex);
                }
            }
        } else {
            PublicStateManager.getInstance().handleIndexUpdate(tool, StateManagerMode.DELETE);
        }
        eventDAO.publishEvent(request.getPublish(), foundUser, tool);
        return tool;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("published")
    @Operation(operationId = "allPublishedContainers", description = "List all published tools.")
    @ApiOperation(value = "List all published tools.", tags = {
        "containers"}, notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> allPublishedContainers(
        @ApiParam(value = "Start index of paging. If not specified in the request, this will start at the beginning of the results.",
                defaultValue = "0") @Min(0) @DefaultValue("0") @QueryParam("offset") Integer offset,
        @ApiParam(value = "Amount of records to return in a given page, limited to "
            + PAGINATION_LIMIT, allowableValues = "range[1,100]", defaultValue = PAGINATION_LIMIT) @Min(1) @Max(MAX_PAGINATION_LIMIT) @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") Integer limit,
        @ApiParam(value = "Filter, this is a search string that filters the results.") @DefaultValue("") @QueryParam("filter") String filter,
        @ApiParam(value = "Sort column") @DefaultValue("stars") @QueryParam("sortCol") String sortCol,
        @ApiParam(value = "Sort order", allowableValues = "asc,desc") @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
        @Context HttpServletResponse response) {
        List<Tool> tools = toolDAO.findAllPublished(offset, limit, filter, sortCol, sortOrder);
        filterContainersForHiddenTags(tools);
        stripContent(tools);
        response.addHeader(LambdaEventResource.X_TOTAL_COUNT, String.valueOf(toolDAO.countAllPublished(Optional.of(filter))));
        response.addHeader(LambdaEventResource.ACCESS_CONTROL_EXPOSE_HEADERS, LambdaEventResource.X_TOTAL_COUNT);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/{repository}/published")
    @Operation(operationId = "getPublishedContainerByPath", summary = "Get a list of published tools by path.", description = "Do not include tool name.",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get a list of published tools by path.", notes = "NO authentication. Do not include tool name.", response = Tool.class, responseContainer = "List")
    public List<Tool> getPublishedContainerByPath(
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tools = toolDAO.findAllByPath(path, true);
        checkNotNull(tools, "Invalid repository path");
        filterContainersForHiddenTags(tools);
        checkCanRead(null, tools);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/{repository}")
    @Operation(operationId = "getContainerByPath", summary = "Get a list of tools by path.", description = "Do not include tool name.",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get a list of tools by path.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Do not include tool name.", response = Tool.class, responseContainer = "List")
    public List<Tool> getContainerByPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tools = toolDAO.findAllByPath(path, false);
        checkNotNull(tools, "Invalid repository path");
        tools.forEach(tool -> checkCanExamine(user, tool));
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/tool/{repository}")
    @Operation(operationId = "getContainerByToolPath", summary = "Get a tool by the specific tool path", description = "Requires full path (including tool name if applicable).",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get a tool by the specific tool path", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Requires full path (including tool name if applicable).", response = Tool.class)
    public Tool getContainerByToolPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @ApiParam(value = "Comma-delimited list of fields to include: validations") @QueryParam("include") String include) {
        Tool tool = toolDAO.findByPath(path, false);
        checkNotNullEntry(tool);
        checkCanExamine(user, tool);

        if (checkIncludes(include, "validations")) {
            tool.getWorkflowVersions().forEach(tag -> Hibernate.initialize(tag.getValidations()));
        }
        Hibernate.initialize(tool.getAliases());
        return tool;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/tool/{repository}/published")
    @Operation(operationId = "getPublishedContainerByToolPath", summary = "Get a published tool by the specific tool path.", description = "Requires full path (including tool name if applicable).")
    @ApiOperation(value = "Get a published tool by the specific tool path.", notes = "Requires full path (including tool name if applicable).", response = Tool.class)
    public Tool getPublishedContainerByToolPath(
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @ApiParam(value = "Comma-delimited list of fields to include: validations") @QueryParam("include") String include, @Context SecurityContext securityContext,
        @Context ContainerRequestContext containerContext) {
        try {
            Tool tool = toolDAO.findByPath(path, true);
            checkNotNullEntry(tool);

            if (checkIncludes(include, "validations")) {
                tool.getWorkflowVersions().forEach(tag -> Hibernate.initialize(tag.getValidations()));
            }
            Hibernate.initialize(tool.getAliases());
            filterContainersForHiddenTags(tool);

            return tool;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new CustomWebApplicationException(path + " not found", HttpStatus.SC_NOT_FOUND);
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/tags")
    @Operation(operationId = "tags", description = "List the tags for a tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "List the tags for a tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Tag.class, responseContainer = "List", hidden = true)
    public List<Tag> tags(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user, @QueryParam("containerId") long containerId) {
        Tool repository = toolDAO.findById(containerId);
        checkNotNullEntry(repository);

        checkCanExamine(user, repository);

        return new ArrayList<>(repository.getWorkflowVersions());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}/dockerfile")
    @Operation(operationId = "dockerfile", description = "Get the corresponding Dockerfile.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get the corresponding Dockerfile.", tags = {
        "containers"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public SourceFile dockerfile(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId, @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, DescriptorLanguage.FileType.DOCKERFILE, user, fileDAO, versionDAO);
    }

    // Add for new descriptor types
    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}/primaryDescriptor")
    @Operation(operationId = "primaryDescriptor", description = "Get the primary descriptor file.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get the primary descriptor file.", tags = {
        "containers"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public SourceFile primaryDescriptor(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId, @QueryParam("tag") String tag, @QueryParam("language") String language) {
        final FileType fileType = DescriptorLanguage.getOptionalFileType(language).orElseThrow(() ->  new CustomWebApplicationException("Language not valid", HttpStatus.SC_BAD_REQUEST));
        return getSourceFile(containerId, tag, fileType, user, fileDAO, versionDAO);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}/descriptor/{relative-path}")
    @Operation(operationId = "secondaryDescriptorPath", description = "Get the corresponding descriptor file.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get the corresponding descriptor file.", tags = {
        "containers"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public SourceFile secondaryDescriptorPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId, @QueryParam("tag") String tag,
        @PathParam("relative-path") String path, @QueryParam("language") String language) {
        final FileType fileType = DescriptorLanguage.getOptionalFileType(language).orElseThrow(() ->  new CustomWebApplicationException("Language not valid", HttpStatus.SC_BAD_REQUEST));
        return getSourceFileByPath(containerId, tag, fileType, path, user, fileDAO, versionDAO);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}/secondaryDescriptors")
    @Operation(operationId = "secondaryDescriptors", description = "Get a list of secondary descriptor files.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get a list of secondary descriptor files.", tags = {
        "containers"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, responseContainer = "List", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public List<SourceFile> secondaryDescriptors(@Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(description = "Tool id") @PathParam("containerId") Long containerId, @QueryParam("tag") String tag,
        @Parameter(description = "Descriptor language") @NotNull @QueryParam("language") DescriptorLanguage language) {
        final FileType fileType = language.getFileType();
        return getAllSecondaryFiles(containerId, tag, fileType, user, fileDAO, versionDAO);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{containerId}/testParameterFiles")
    @Operation(operationId = "getTestParameterFiles", description = "Get the corresponding test parameter files.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get the corresponding test parameter files.", tags = {
        "containers"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, responseContainer = "List", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public List<SourceFile> getTestParameterFiles(@Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(description = "Tool id") @PathParam("containerId") Long containerId, @QueryParam("tag") String tag,
        @Parameter(description = "Descriptor Type") @NotNull @QueryParam("descriptorType") DescriptorLanguage descriptorLanguage) {
        final FileType testParameterType = descriptorLanguage.getTestParamType();
        return getAllSourceFiles(containerId, tag, testParameterType, user, fileDAO, versionDAO);
    }

    @Override
    public boolean canExamine(User user, Entry tool) {
        boolean result = EntryVersionHelper.super.canExamine(user, tool);
        if (!result) {
            LOG.info("permissions are not yet tool aware");
        }
        return result;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/testParameterFiles")
    @Operation(operationId = "addTestParameterFiles", description = "Add test parameter files to a tag.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Add test parameter files to a tag.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> addTestParameterFiles(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody,
        @QueryParam("tagName") String tagName,
        @ApiParam(value = "Descriptor Type", required = true, allowableValues = "CWL, WDL") @QueryParam("descriptorType") String descriptorType) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        checkNotHosted(tool);
        checkCanWrite(user, tool);
        Optional<Tag> firstTag = tool.getWorkflowVersions().stream().filter((Tag v) -> v.getName().equals(tagName)).findFirst();

        if (firstTag.isEmpty()) {
            String msg = "The tag '" + Utilities.cleanForLogging(tagName) + "' for tool '" + tool.getToolPath() + "' does not exist.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Tag tag = firstTag.get();
        checkNotFrozen(tag);
        Set<SourceFile> sourceFiles = tag.getSourceFiles();

        // Add new test parameter files
        FileType fileType = DescriptorLanguage.getTestFileTypeFromDescriptorLanguageString(descriptorType);
        createTestParameters(testParameterPaths, tag, sourceFiles, fileType, fileDAO);
        PublicStateManager.getInstance().handleIndexUpdate(tool, StateManagerMode.UPDATE);
        return tag.getSourceFiles();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}/testParameterFiles")
    @Operation(operationId = "deleteTestParameterFiles", description = "Delete test parameter files to a tag.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Delete test parameter files to a tag.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> deleteTestParameterFiles(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
        @QueryParam("tagName") String tagName,
        @ApiParam(value = "Descriptor Type", required = true, allowableValues = "CWL, WDL") @QueryParam("descriptorType") String descriptorType) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        checkNotHosted(tool);
        checkCanWrite(user, tool);
        Optional<Tag> firstTag = tool.getWorkflowVersions().stream().filter((Tag v) -> v.getName().equals(tagName)).findFirst();

        if (firstTag.isEmpty()) {
            String msg = "The tag '\" + Utilities.cleanForLogging(tagName) + \"' for tool '\" + tool.getToolPath() + \"' does not exist.\")";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Tag tag = firstTag.get();
        checkNotFrozen(tag);
        Set<SourceFile> sourceFiles = tag.getSourceFiles();

        // Remove test parameter files
        FileType fileType = DescriptorLanguage.getTestFileTypeFromDescriptorLanguageString(descriptorType);
        for (String path : testParameterPaths) {
            boolean fileDeleted = sourceFiles.removeIf((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType);
            if (!fileDeleted) {
                throw new CustomWebApplicationException("There are no existing test parameter files with the path: " + path, HttpStatus.SC_NOT_FOUND);
            }
        }

        return tag.getSourceFiles();
    }

    @PUT
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Path("/{containerId}/star")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "starEntry", description = "Star a tool.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Star a tool.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public void starEntry(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to star.", required = true) @Parameter(description = "Tool to star.", required = true) @PathParam("containerId") Long containerId,
        @ApiParam(value = "StarRequest to star a repo for a user", required = true) @Parameter(description = "StarRequest to star a repo for a user", required = true) StarRequest request) {
        Tool tool = toolDAO.findById(containerId);
        if (request.getStar()) {
            starEntryHelper(tool, user, "tool", tool.getToolPath());
        } else {
            unstarEntryHelper(tool, user, "tool", tool.getToolPath());
        }
        PublicStateManager.getInstance().handleIndexUpdate(tool, StateManagerMode.UPDATE);
    }

    @GET
    @Path("/{containerId}/starredUsers")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getStarredUsers", description = "Returns list of users who starred a tool.")
    @ApiOperation(value = "Returns list of users who starred a tool.", response = User.class, responseContainer = "List")
    public Set<User> getStarredUsers(
        @ApiParam(value = "Tool to grab starred users for.", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkNotNullEntry(tool);
        checkCanRead(tool);
        return tool.getStarredUsers();
    }

    @Override
    public ToolDAO getDAO() {
        return this.toolDAO;
    }

    private String convertHttpsToSsh(String url) {
        Pattern p = Pattern.compile("^(https?:)?//(www\\.)?(github\\.com|bitbucket\\.org|gitlab\\.com)/([\\w-.]+)/([\\w-.]+)$");
        Matcher m = p.matcher(url);
        if (!m.find()) {
            LOG.info("Cannot parse HTTPS url: " + url);
            return null;
        }

        // These correspond to the positions of the pattern matcher
        final int sourceIndex = 3;
        final int usernameIndex = 4;
        final int reponameIndex = 5;

        String source = m.group(sourceIndex);
        String gitUsername = m.group(usernameIndex);
        String gitRepository = m.group(reponameIndex);

        return "git@" + source + ":" + gitUsername + "/" + gitRepository + ".git";
    }

    /**
     * Determines if the given URL is a git URL
     *
     * @param url
     * @return is url of the format git@source:gitUsername/gitRepository
     */
    private static boolean isGit(String url) {
        final Optional<Map<String, String>> stringStringGitUrlMap = SourceCodeRepoFactory
            .parseGitUrl(url);
        return !stringStringGitUrlMap.isEmpty();
    }

    /**
     * Checks if a user owns a given quay repo or is part of an organization that owns the quay repo
     *
     * @param tool
     * @param userId
     * @return
     */
    private boolean checkIfUserOwns(final Tool tool, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        // get quay token
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO);

        if (quayToken == null && Objects.equals(tool.getRegistry(), Registry.QUAY_IO.getDockerPath())) {
            LOG.info("WARNING: QUAY.IO token not found!");
            throw new CustomWebApplicationException("A valid Quay.io token is required to add this tool.", HttpStatus.SC_BAD_REQUEST);
        } else if (quayToken != null) {
            // set up
            QuayImageRegistry factory = new QuayImageRegistry(quayToken);

            // get quay username
            String quayUsername = quayToken.getUsername();

            // call quay api, check if user owns or is part of owning organization
            final Optional<QuayRepo> toolFromQuay = factory.getToolFromQuay(tool);

            if (toolFromQuay.isPresent()) {
                final QuayRepo quayInfo = toolFromQuay.get();
                String namespace = quayInfo.getNamespace();
                boolean isOrg = quayInfo.isIsOrganization();

                if (isOrg) {
                    List<String> namespaces = factory.getNamespaces();
                    return namespaces.stream().anyMatch(nm -> nm.equals(namespace));
                } else {
                    return (namespace.equals(quayUsername));
                }
            }
        }
        return false;
    }

    private void checkNotHosted(Tool tool) {
        if (tool.getMode() == ToolMode.HOSTED) {
            throw new CustomWebApplicationException("Cannot modify hosted entries this way", HttpStatus.SC_BAD_REQUEST);
        }
    }

    // Checks that the Amazon ECR docker path matches the provided private access.
    // If the tool has Amazon ECR's public docker path, public.ecr.aws, the tool must be public
    // If the tool has Amazon ECR's private docker path, *.dkr.ecr.*.amazonaws.com, the tool must be private
    private void checkAmazonECRPrivateAccess(String amazonECRDockerPath, boolean privateAccess) {
        // Public Amazon ECR tool (public.ecr.aws docker path) can't be set to private
        if (Registry.AMAZON_ECR.getDockerPath().equals(amazonECRDockerPath) && privateAccess) {
            throw new CustomWebApplicationException("The public Amazon ECR tool cannot be set to private.", HttpStatus.SC_BAD_REQUEST);
        }

        // Private Amazon ECR tool with custom docker path can't be set to public
        if (AMAZON_ECR_PRIVATE_REGISTRY_REGEX.matcher(amazonECRDockerPath).matches() && !privateAccess) {
            throw new CustomWebApplicationException("The private Amazon ECR tool cannot be set to public.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{toolId}/zip/{tagId}")
    @Operation(operationId = "getToolZip", description = "Download a ZIP file of a tool and all associated files.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Download a ZIP file of a tool and all associated files.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @Produces("application/zip")
    public Response getToolZip(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "toolId", required = true) @PathParam("toolId") Long toolId,
        @ApiParam(value = "tagId", required = true) @PathParam("tagId") Long tagId) {

        Tool tool = toolDAO.findById(toolId);
        checkNotNullEntry(tool);
        checkCanRead(user, tool);

        Tag tag = tool.getWorkflowVersions().stream().filter(innertag -> innertag.getId() == tagId).findFirst()
            .orElseThrow(() -> new CustomWebApplicationException("Could not find tag", HttpStatus.SC_NOT_FOUND));
        Set<SourceFile> sourceFiles = tag.getSourceFiles();
        if (sourceFiles == null || sourceFiles.size() == 0) {
            throw new CustomWebApplicationException("no files found to zip", HttpStatus.SC_NO_CONTENT);
        }

        String fileName = EntryVersionHelper.generateZipFileName(tool.getToolPath(), tag.getName());
        java.nio.file.Path path = Paths.get(tag.getWorkingDirectory());

        return Response.ok().entity((StreamingOutput) output -> writeStreamAsZip(sourceFiles, output, path))
            .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").build();
    }
}
