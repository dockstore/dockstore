package io.dockstore.webservice.permissions.sam;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.permissions.Permission;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.swagger.sam.client.ApiClient;
import io.swagger.sam.client.ApiException;
import io.swagger.sam.client.JSON;
import io.swagger.sam.client.api.ResourcesApi;
import io.swagger.sam.client.model.AccessPolicyMembership;
import io.swagger.sam.client.model.AccessPolicyResponseEntry;
import io.swagger.sam.client.model.ErrorReport;
import io.swagger.sam.client.model.ResourceAndAccessPolicy;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>An implementation of the {@link PermissionsInterface} that makes
 * calls to SAM.</p>
 *
 * <p>
 *     Calls to SAM use a Google access token. This token can be acquired in 3 ways:
 *     <ul>
 *         <li>Using the user's Google access token in the database.</li>
 *         <li>Minting and using a new Google access token with the user's refresh token in the
 *         database. This is only done when we've detected the Google access token has expired.</li>
 *         <li>A call from the "outside", e.g., Terra accesses a Dockstore API with a Google access
 *         token minted in Terra. In this case, the access token is stored in the transient
 *         <code>User.temporaryCredential</code> field. Note that this use case is technically
 *         possible but not currently used by Terra. Also note that we check the token's "audience"
 *         to ensure only approved Google clients can access Dockstore this way.</li>
 *     </ul>
 * </p>
 *
 * <p>What can go wrong with auth?
 * <ul>
 *     <li>The user's Google account is not registered in Terra/SAM</li>
 *     <li>The user's Google account in Terra/SAM has been disabled</li>
 *     <li>The user's Google refresh token has expired. We are unable to mint a new access token
 *     for the user. The user must relink their Google account in Dockstore to get a new refresh
 *     token.</li>
 * </ul>
 * </p>
 *
 * <p>
 *   We want the user to know their refresh token has expired, but we don't want to fill up the UI
 *   with errors for all the SAM APIs. The following public methods will throw an exception for an expired
 *   refresh token. The ideas is that write operations should throw an error, so you know they failed.
 *   The exception being <code>workflowsSharedWithUser</code>, because we'd want the UI to display
 *   a warning if we're unable to fetch shared workflows.
 *   <ul>
 *       <li>setPermission</li>
 *       <li>removePermission</li>
 *       <li>workflowsSharedWithUser</li>
 *       <li>selfDestruct</li>
 *   </ul>
 *   The following public methods will NOT throw an exception for an expired refresh token. The
 *   idea is that read-only operations shouldn't cause errors.
 *   <ul>
 *       <li>getPermissionsForWorkflow</li>
 *       <li>getActionsForWorkflow</li>
 *       <li>canDoAction</li>
 *       <li>isSharing</li>
 *   </ul>
 * </p>
 */
public class SamPermissionsImpl implements PermissionsInterface {

    static final String GOOGLE_ACCOUNT_MUST_BE_LINKED = "Google account must be linked";

    private static final Logger LOG = LoggerFactory.getLogger(SamPermissionsImpl.class);

    /**
     * A map of SAM policy names to Dockstore roles.
     */
    private static Map<String, Role> samPermissionMap = new HashMap<>();

    static {
        samPermissionMap.put(SamConstants.OWNER_POLICY, Role.OWNER);
        samPermissionMap.put(SamConstants.WRITE_POLICY, Role.WRITER);
        samPermissionMap.put(SamConstants.READ_POLICY, Role.READER);
    }

    /**
     * A map of Dockstore roles to SAM policy names. Created by swapping the keys and values
     * in the <code>samPermissionMap</code>.
     */
    private static Map<Role, String> permissionSamMap = samPermissionMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, c -> c.getKey()));

    private DockstoreWebserviceConfiguration config;
    private final TokenDAO tokenDAO;

    public SamPermissionsImpl(TokenDAO tokenDAO, DockstoreWebserviceConfiguration config) {
        this.tokenDAO = tokenDAO;
        this.config = config;
    }

    /**
     * Calls SAM to add the email in <code>permission</code> to the appropriate policy, based on the role
     * in the <code>permission</code> for the SAM resource for <code>workflow</code>.
     *
     * In SAM an email can belong to multiple policies for the same resource. This can lead to confusion in the Dockstore
     * UI. If a user belongs to the writer policy, then gets added to the reader policy as well, the user will still have
     * write permissions, which is probably not the intended behavior.
     *
     * To avoid this, when setting a permission, the code removes the user from any other policies the user may belong to.
     *
     * @param requester -- the requester, who must be an owner of <code>workflow</code> or an admin
     * @param workflow the workflow
     * @param permission -- the email and the permission for that email
     * @return a list of permissions
     * @throws CustomWebApplicationException
     */
    @Override
    public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
        checkHasGoogleToken(requester);
        // If original owner, you can't mess with their permissions
        checkEmailNotOriginalOwner(permission.getEmail(), workflow);
        ResourcesApi resourcesApi = getResourcesApi(requester); // Intentionally throwing if unable to get token
        try {
            final String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());

            final List<AccessPolicyResponseEntry> resourcePolicies = ensureResourceExists(workflow, requester, resourcesApi,
                    encodedPath);
            final List<AccessPolicyResponseEntry> policiesNewUserBelongsTo = resourcePolicies
                    .stream().filter(entry -> entry.getPolicy().getMemberEmails().contains(permission.getEmail()))
                    .toList();
            final String samPolicyName = permissionSamMap.get(permission.getRole());
            ensurePolicyExists(resourcePolicies, samPolicyName, encodedPath, resourcesApi);
            // If the email does not already belong to the policy, add it.
            if (policiesNewUserBelongsTo.stream().noneMatch(entry -> entry.getPolicyName().equals(samPolicyName))) {
                resourcesApi.addUserToPolicy(SamConstants.RESOURCE_TYPE, encodedPath, samPolicyName,
                        permission.getEmail());
            }
            // If the email belongs to other policies, remove it from them so that the one we are setting is the only applicable one.
            for (AccessPolicyResponseEntry entry : policiesNewUserBelongsTo) {
                if (!entry.getPolicyName().equals(samPolicyName)) {
                    resourcesApi.removeUserFromPolicy(SamConstants.RESOURCE_TYPE, encodedPath, entry.getPolicyName(), permission.getEmail());
                }
            }
            return getPermissionsForWorkflow(requester, workflow);
        } catch (ApiException e) {
            String errorMessage = readValue(e, ErrorReport.class).map(errorReport -> errorReport.getMessage())
                    .orElse("Error setting permission");
            LOG.error(errorMessage, e);
            throw new CustomWebApplicationException(errorMessage, e.getCode());
        }
    }

    /**
     * Creates a Swagger client for the SAM Resources API, with the authorization header set to the user's Google
     * access token. Throws a <code>CustomWebApplicationException</code> if unable to get an access
     * token.
     * @param requester
     * @return a resources Swagger API client
     * @throw CustomWebApplicationException
     */
    ResourcesApi getResourcesApi(User requester) {
        return new ResourcesApi(getApiClient(requester));
    }

    private void ensurePolicyExists(List<AccessPolicyResponseEntry> policyList, String policyName, String resourceId, ResourcesApi resourcesApi)
            throws ApiException {
        // Owner policy is always created when creating a resource. Add an additional safeguard to avoid creating a second owner policy,
        // which leads to #1805
        if (!SamConstants.OWNER_POLICY.equals(policyName)
                && policyList.stream().noneMatch(policy -> policyName.equals(policy.getPolicyName()))) {
            addPolicy(resourcesApi, resourceId, policyName);
        }
    }

    private List<AccessPolicyResponseEntry> ensureResourceExists(Workflow workflow, User requester, ResourcesApi resourcesApi, String encodedPath) {
        try {
            return resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, encodedPath);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.SC_NOT_FOUND) {
                initializePermission(workflow, requester);
                try {
                    return resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, encodedPath);
                } catch (ApiException e1) {
                    throw new CustomWebApplicationException("Error listing permissions", e1.getCode());
                }
            } else {
                throw new CustomWebApplicationException("Error listing permissions", e.getCode());
            }
        }
    }

    /**
     * Returns a map of roles to workflow paths the user has access to. Throws a
     * <code>CustomWebApplicationException</code> if unable to get an access token.
     * @param user
     * @return a map of roles to workflow paths
     * @throws CustomWebApplicationException
     */
    @Override
    public Map<Role, List<String>> workflowsSharedWithUser(User user) {
        if (!hasGoogleToken(user)) {
            return Collections.emptyMap();
        }
        ResourcesApi resourcesApi = getResourcesApi(user); // Intentionally throwing if unable to get token
        try {
            List<ResourceAndAccessPolicy> resourceAndAccessPolicies = resourcesApi.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE);
            return weedOutDuplicateResourceIds(resourceAndAccessPolicies).stream()
                    .collect(Collectors.groupingBy(ResourceAndAccessPolicy::getAccessPolicyName)).entrySet().stream()
                    .collect(Collectors.toMap(e -> samPolicyNameToRole(e.getKey()), e -> e.getValue().stream().map(r -> {
                        try {
                            return URLDecoder.decode(r.getResourceId().substring(SamConstants.ENCODED_WORKFLOW_PREFIX.length()), "UTF-8");
                        } catch (UnsupportedEncodingException e1) {
                            return null;
                        }
                    }).collect(Collectors.toList())));
        } catch (ApiException e) {
            final String message = "Error getting shared workflows";
            if (userNotAuthorizedForSam(e)) {
                LOG.debug(message, e);
                // If user is unauthorized in SAM, then nothing has been shared with that user
                return Collections.emptyMap();
            }
            LOG.error(message, e);
            throw new CustomWebApplicationException(message, e.getCode());
        }
    }

    private boolean userNotAuthorizedForSam(ApiException e) {
        // TLDR; 401 disabled or not accepted TOS; 403 user does not exist, i.e., not registered in SAM
        // See https://github.com/broadinstitute/sam/blob/1c3c1a3f3e973895de9ba08e6c755edbb04632db/src/main/scala/org/broadinstitute/dsde/workbench/sam/api/SamUserDirectives.scala#L31-L30
        return e.getCode() == HttpStatus.SC_UNAUTHORIZED || e.getCode() == HttpStatus.SC_FORBIDDEN;
    }

    /**
     * Weeds out duplicate resource ids from <code>resourceAndAccessPolicies</code>, giving priority to the more
     * privileged role when there are duplicates.
     *
     * There might be a more elegant way to implement this with streams, but this works for now.
     *
     * @param resourceAndAccessPolicies
     * @return
     */
    private Collection<ResourceAndAccessPolicy> weedOutDuplicateResourceIds(List<ResourceAndAccessPolicy> resourceAndAccessPolicies) {
        final Map<String, ResourceAndAccessPolicy> map = new HashMap<>();
        for (ResourceAndAccessPolicy policy : resourceAndAccessPolicies) {
            final ResourceAndAccessPolicy existing = map.get(policy.getResourceId());
            if (shouldPutPolicy(existing, policy)) {
                map.put(policy.getResourceId(), policy);
            }
        }
        return map.values();
    }

    private boolean shouldPutPolicy(ResourceAndAccessPolicy existing, ResourceAndAccessPolicy candidate) {
        if (existing == null) {
            return true;
        } else {
            final Role candidateRole = samPolicyNameToRole(candidate.getAccessPolicyName());
            final Role existingRole = samPolicyNameToRole(existing.getAccessPolicyName());
            return candidateRole.compareTo(existingRole) < 0;
        }
    }

    /**
     * Gets the permissions for a workflow. The <code>user</code> must either be one of the
     * workflow's users, in <code>workflow.getUsers()</code>, or have the Role.OWNER permission
     * via SAM.
     *
     * @param user the user, who must be an owner of the workflow
     * @param workflow the workflow
     * @return a list of permissions
     * @throw CustomWebApplicationException
     */
    @Override
    public List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        List<Permission> samPermissions = getSamPermissions(user, workflow);
        if (samPermissions.isEmpty() || !isSamOwner(user, samPermissions)) {
            // Super method checks if user is workflow user
            return PermissionsInterface.super.getPermissionsForWorkflow(user, workflow);
        }
        // getOriginalOwnersForWorkflow does not check if user is in workflow.getUsers(), because
        // they have access to the workflow via SAM.
        final List<Permission> dockstoreOwners = getOriginalOwnersForWorkflow(workflow);
        return PermissionsInterface.mergePermissions(dockstoreOwners, samPermissions);
    }

    private List<Permission> getSamPermissions(User user, Workflow workflow) {
        if (hasGoogleToken(user)) {
            try {
                final ResourcesApi resourcesApi = getResourcesApi(user);
                final String encoded = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
                return accessPolicyResponseEntryToUserPermissions(
                        resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, encoded));
            } catch (ApiException e) {
                final String errorGettingPermissions = "Error getting permissions";
                LOG.error(errorGettingPermissions, e);
                // 404 - SAM resource has not been created, or user doesn't have access;
                if (userNotAuthorizedForSam(e) && e.getCode() != HttpStatus.SC_NOT_FOUND) {
                    throw new CustomWebApplicationException(errorGettingPermissions, e.getCode());
                }
            }
        }
        return List.of();
    }

    private boolean isSamOwner(User user, List<Permission> permissions) {
        final Optional<String> email = userIdForSharing(user);
        return email.isPresent() && permissions.stream().anyMatch(permission ->
            permission.getEmail().equals(email.get()) && permission.getRole() == Role.OWNER);
    }

    @Override
    public List<Role.Action> getActionsForWorkflow(User user, Workflow workflow) {
        List<Role.Action> list = new ArrayList<>();
        if (workflow.getUsers().contains(user) || canDoAction(user, workflow, Role.Action.SHARE)) {
            // Shortcut to avoid multiple calls; if we can share, we're an owner and can do all actions
            list.addAll(Arrays.asList(Role.Action.values()));
        } else if (canDoAction(user, workflow, Role.Action.WRITE)) {
            // If we can write, we can read
            list.add(Role.Action.WRITE);
            list.add(Role.Action.READ);
        } else if (canDoAction(user, workflow, Role.Action.READ)) {
            list.add(Role.Action.READ);
        }
        return list;
    }

    @Override
    public void removePermission(User user, Workflow workflow, String email, Role role) {
        checkHasGoogleToken(user);
        checkEmailNotOriginalOwner(email, workflow);
        ResourcesApi resourcesApi = getResourcesApi(user); // Intentionally throwing if unable to get token
        String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
        try {
            List<AccessPolicyResponseEntry> entries = resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, encodedPath);
            for (AccessPolicyResponseEntry entry : entries) {
                if (permissionSamMap.get(role).equals(entry.getPolicyName())) {
                    if (entry.getPolicy().getMemberEmails().contains(email)) {
                        resourcesApi.removeUserFromPolicy(SamConstants.RESOURCE_TYPE, encodedPath, entry.getPolicyName(), email);
                    }
                }
            }
        } catch (ApiException e) {
            LOG.error(MessageFormat.format("Error removing {0} from workflow {1}", email, encodedPath), e);
            throw new CustomWebApplicationException("Error removing permissions", e.getCode());
        }
    }

    private void checkHasGoogleToken(final User user) {
        if (!hasGoogleToken(user)) {
            throw new CustomWebApplicationException(GOOGLE_ACCOUNT_MUST_BE_LINKED, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Throws a CustomWebApplication if <code>email</code> is an original owner, a user
     * that owns <code>workflow</code> in Dockstore.
     *
     * The {@link User} class has a <code>username</code> property, which currently can get set to either the user's GitHub id
     * or Google email, depending on the order that the user associated those accounts. The method first tries to match
     * comparing the email against the username -- if it doesn't find it, it looks in the user profiles for the email.
     *
     *
     * @param email must not be null
     * @param workflow
     */
    void checkEmailNotOriginalOwner(String email, Workflow workflow) {
        assert email != null;
        final boolean isOwner = workflow.getUsers().stream().anyMatch(u -> {
            if (email.equals(u.getUsername())) {
                return true;
            }
            final User.Profile profile = u.getUserProfiles().get(TokenType.GOOGLE_COM.toString());
            return profile != null && email.equals(profile.email);
        });
        if (isOwner) {
            throw new CustomWebApplicationException(email + " is an original owner and their permissions cannot be modified", HttpStatus.SC_FORBIDDEN);
        }
    }

    private void initializePermission(Workflow workflow, User user) {
        ResourcesApi resourcesApi = getResourcesApi(user);
        String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
        try {
            resourcesApi.createResourceWithDefaults(SamConstants.RESOURCE_TYPE, encodedPath);
            addPolicy(resourcesApi, encodedPath, SamConstants.WRITE_POLICY);
            addPolicy(resourcesApi, encodedPath, SamConstants.READ_POLICY);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.SC_CONFLICT) {
                // The SAM resource already exists, but it is owned a different user.
                // This should never occur if only using Dockstore APIs, but could happen if a user accesses SAM API directly.
                final String message = MessageFormat.format(
                        "An unexpected error occurred. Please send a private message to \"admins\" at https://discuss.dockstore.org and mention \"SAM {0}\"",
                        encodedPath);
                throw new CustomWebApplicationException(message, HttpStatus.SC_CONFLICT);
            } else {
                throw new CustomWebApplicationException("Error initializing permissions", e.getCode());
            }
        }
    }

    private void addPolicy(ResourcesApi resourcesApi, String resourceId, String policyName) throws ApiException {
        final AccessPolicyMembership policy = new AccessPolicyMembership();
        policy.addRolesItem(policyName); // The role name and the policy name are the same
        resourcesApi.overwritePolicy(SamConstants.RESOURCE_TYPE, resourceId, policyName, policy);

    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
        if (user == null) {
            return false;
        }
        if (hasGoogleToken(user)) {
            try {
                final ResourcesApi resourcesApi = getResourcesApi(user);
                final String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
                return resourcesApi.resourceAction(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.toSamAction(action));
            } catch (ApiException e) {
                LOG.error("Error checking for resource action in SAM", e);
            }
        }
        return false;
    }

    @Override
    public void selfDestruct(User user) {
        if (hasGoogleToken(user)) {
            final ResourcesApi resourcesApi = getResourcesApi(user); // Intentionally throwing if unable to get token
            try {
                final List<String> resourceIds = ownedResourceIds(resourcesApi);
                if (!userIsOnlyMember(resourceIds, resourcesApi)) {
                    throw new CustomWebApplicationException("The user is sharing at least one workflow and cannot be deleted.",
                            HttpStatus.SC_BAD_REQUEST);
                }
                for (String resourceId : resourceIds) {
                    resourcesApi.deleteResource(SamConstants.RESOURCE_TYPE, resourceId);
                }
            } catch (ApiException e) {
                throw new CustomWebApplicationException("Error deleting user", e.getCode());
            }
        }
    }

    @Override
    public boolean isSharing(User user) {
        if (!hasGoogleToken(user)) {
            return false;
        }
        try {
            final ResourcesApi resourcesApi = getResourcesApi(user);
            final List<String> resourceIds = ownedResourceIds(resourcesApi);
            return !userIsOnlyMember(resourceIds, resourcesApi);
        } catch (ApiException e) {
            if (userNotAuthorizedForSam(e)) {
                LOG.debug("User not authorized for sam", e);
                return false;
            }
            LOG.error("Error fetching user's shared resources", e);
            // Unknown error, assume they could be sharing to be safe
            return true;
        }
    }

    @Override
    public Optional<String> userIdForSharing(final User user) {
        final User.Profile profile = user.getUserProfiles().get(TokenType.GOOGLE_COM.toString());
        if (profile != null) {
            return Optional.ofNullable(profile.email);
        }
        return Optional.empty();
    }

    boolean userIsOnlyMember(List<String> resourceIds, ResourcesApi resourcesApi) {
        for (String resourceId : resourceIds) {
            final List<AccessPolicyResponseEntry> entries;
            try {
                entries = resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, resourceId);
            } catch (ApiException e) {
                LOG.error(MessageFormat.format("Error getting resource policies for {}", resourceId), e);
                throw new CustomWebApplicationException("Error getting resource policies", e.getCode());
            }
            if (entries.stream().anyMatch(entry -> {
                if (entry.getPolicyName().equals(SamConstants.OWNER_POLICY)) {
                    return entry.getPolicy().getMemberEmails().size() > 1; // There should be one owner
                } else {
                    return entry.getPolicy().getMemberEmails().size() > 0;
                }
            })) {
                return false;
            }
        }
        return true;
    }

    private List<String> ownedResourceIds(ResourcesApi resourcesApi) throws ApiException {
        try {
            return resourcesApi.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE)
                    .stream()
                    .filter(p -> SamConstants.OWNER_POLICY.equals(p.getAccessPolicyName()))
                    .map(p -> p.getResourceId())
                    .collect(Collectors.toList());
        } catch (ApiException e) {
            if (userNotAuthorizedForSam(e)) {
                // User is not in SAM
                return Collections.emptyList();
            }
            throw e;
        }
    }

    /**
     * Creates a Swagger API client for SAM, with the authorization set to the user's Google
     * access token. Throws a <code>CustomWebApplicationException</code> if unable to get a valid Google access
     * token, which can occur if the Google refresh token has expired.
     * @param user
     * @return a Swagger API client
     * @throw CustomWebApplicationException
     */
    private ApiClient getApiClient(User user) {
        ApiClient apiClient = new ApiClient() {
            @Override
            protected void performAdditionalClientConfiguration(ClientConfig clientConfig) {
                // Calling ResourcesApi.addUserToPolicy invokes PUT without a body, which will fail
                // without this:
                clientConfig.property(ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);
            }
        };
        apiClient.setBasePath(config.getSamConfiguration().getBasepath());
        return googleAccessToken(user).map(credentials -> {
            apiClient.setAccessToken(credentials);
            return apiClient;
        }).orElseThrow(() -> new CustomWebApplicationException("Could not get Google access token. Try relinking your Google account.", HttpStatus.SC_UNAUTHORIZED));
    }

    private String encodedWorkflowResource(Workflow workflow, ApiClient apiClient) {
        final StringBuilder sb = new StringBuilder(SamConstants.WORKFLOW_PREFIX);
        sb.append(workflow.getWorkflowPath());
        return apiClient.escapeString(sb.toString());
    }

    /**
     * Gets a non-expired access token, which may entail refreshing the token. If the token
     * is refreshed, the access token is updated in the token table.
     *
     * @param user
     * @return
     */
    Optional<String> googleAccessToken(User user) {
        if (user.getTemporaryCredential() != null) {
            return Optional.of(user.getTemporaryCredential());
        }
        Token token = googleToken(user);
        if (token != null) {
            return GoogleHelper.getValidAccessToken(token).map(accessToken -> {
                if (!accessToken.equals(token.getToken())) {
                    token.setContent(accessToken);
                    tokenDAO.update(token);
                }
                return Optional.of(accessToken);
            }).orElse(Optional.empty());
        }
        return Optional.empty();
    }

    Token googleToken(User user) {
        List<Token> tokens = tokenDAO.findByUserId(user.getId());
        return Token.extractToken(tokens, TokenType.GOOGLE_COM);
    }

    boolean hasGoogleToken(User user) {
        return user.getTemporaryCredential() != null || googleToken(user) != null;
    }

    /**
     * Converts the response from SAM into a list of Dockstore {@link Permission}
     *
     * @param accessPolicyList
     * @return
     */
    List<Permission> accessPolicyResponseEntryToUserPermissions(List<AccessPolicyResponseEntry> accessPolicyList) {
        return accessPolicyList.stream().map(accessPolicy -> {
            Role role = samPermissionMap.get(accessPolicy.getPolicy().getRoles().get(0));
            return accessPolicy.getPolicy().getMemberEmails().stream().map(email -> {
                Permission permission = new Permission();
                permission.setRole(role);
                permission.setEmail(email);
                return permission;
            });
        }).flatMap(s -> s).collect(Collectors.toList());

    }

    /**
     * Removes duplicate emails from <code>permissionList</code>. If there are duplicates,
     * leaves the one with the most privileged role.
     * <p>
     * The Dockstore UI will be simplified to only show one role; while the SAM API support
     *
     * @param permissionList
     * @return
     */
    List<Permission> removeDuplicateEmails(List<Permission> permissionList) {
        // A map of email to permissions.
        final Map<String, Permission> map = new HashMap<>();
        permissionList.forEach(permission -> {
            final Permission existing = map.get(permission.getEmail());
            if (existing == null || existing.getRole().ordinal() > permission.getRole().ordinal()) {
                map.put(permission.getEmail(), permission);
            }
        });
        return new ArrayList<>(map.values());
    }

    <T> Optional<T> readValue(ApiException e, Class<T> clazz) {
        String body = e.getResponseBody();
        return readValue(body, clazz);
    }

    <T> Optional<T> readValue(String body, Class<T> clazz) {
        try {
            ObjectMapper context = new JSON().getContext(clazz);
            return Optional.of(context.readValue(body, clazz));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Role samPolicyNameToRole(String policyName) {
        if (SamConstants.READ_POLICY.equals(policyName)) {
            return Role.READER;
        } else if (SamConstants.WRITE_POLICY.equals(policyName)) {
            return Role.WRITER;
        } else if (SamConstants.OWNER_POLICY.equals(policyName)) {
            return Role.OWNER;
        }
        return null;
    }

}
