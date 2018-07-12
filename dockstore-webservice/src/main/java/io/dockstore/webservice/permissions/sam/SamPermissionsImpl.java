package io.dockstore.webservice.permissions.sam;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import org.apache.http.HttpStatus;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link PermissionsInterface} that makes
 * calls to SAM.
 */
public class SamPermissionsImpl implements PermissionsInterface {

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

    @Override
    public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
        ResourcesApi resourcesApi = getResourcesApi(requester);
        try {
            final String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());

            ensureResourceExists(workflow, requester, resourcesApi, encodedPath);

            resourcesApi.addUserToPolicy(SamConstants.RESOURCE_TYPE, encodedPath, permissionSamMap.get(permission.getRole()),
                    permission.getEmail());
            return getPermissionsForWorkflow(requester, workflow);
        } catch (ApiException e) {
            String errorMessage = readValue(e, ErrorReport.class).map(errorReport -> errorReport.getMessage())
                    .orElse("Error setting permission");
            LOG.error(errorMessage, e);
            throw new CustomWebApplicationException(errorMessage, e.getCode());
        }
    }

    ResourcesApi getResourcesApi(User requester) {
        return new ResourcesApi(getApiClient(requester));
    }

    private void ensureResourceExists(Workflow workflow, User requester, ResourcesApi resourcesApi, String encodedPath) {
        try {
            resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, encodedPath);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.SC_NOT_FOUND) {
                initializePermission(workflow, requester);
            } else {
                throw new CustomWebApplicationException("Error listing permissions", e.getCode());
            }
        }
    }

    @Override
    public Map<Role, List<String>> workflowsSharedWithUser(User user) {
        if (googleToken(user) == null) {
            return Collections.emptyMap();
        }
        ResourcesApi resourcesApi = getResourcesApi(user);
        try {
            List<ResourceAndAccessPolicy> resourceAndAccessPolicies = resourcesApi.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE);
            return resourceAndAccessPolicies.stream().collect(Collectors.groupingBy(ResourceAndAccessPolicy::getAccessPolicyName))
                    .entrySet().stream()
                    .collect(Collectors.toMap(e -> samPolicyNameToRole(e.getKey()), e -> e.getValue().stream().map(r -> {
                        try {
                            return URLDecoder.decode(r.getResourceId().substring(SamConstants.ENCODED_WORKFLOW_PREFIX.length()), "UTF-8");
                        } catch (UnsupportedEncodingException e1) {
                            return null;
                        }
                    }).collect(Collectors.toList())));
        } catch (ApiException e) {
            LOG.error("Error getting shared workflows", e);
            throw new CustomWebApplicationException("Error getting shared workflows", e.getCode());
        }
    }

    @Override
    public List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        final List<Permission> dockstoreOwners = PermissionsInterface.getOriginalOwnersForWorkflow(workflow);
        ResourcesApi resourcesApi = getResourcesApi(user);
        try {
            String encoded = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
            final List<Permission> samPermissions = accessPolicyResponseEntryToUserPermissions(
                    resourcesApi.listResourcePolicies(SamConstants.RESOURCE_TYPE, encoded));
            return PermissionsInterface.mergePermissions(dockstoreOwners, samPermissions);
        } catch (ApiException e) {
            // If 404, the SAM resource has not yet been created, so just return Dockstore owners
            if (e.getCode() != HttpStatus.SC_NOT_FOUND) {
                throw new CustomWebApplicationException("Error getting permissions", e.getCode());
            }
        }
        return dockstoreOwners;
    }

    @Override
    public void removePermission(User user, Workflow workflow, String email, Role role) {
        PermissionsInterface.checkUserNotOriginalOwner(email, workflow);
        ResourcesApi resourcesApi = getResourcesApi(user);
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

    private void initializePermission(Workflow workflow, User user) {
        ResourcesApi resourcesApi = getResourcesApi(user);
        String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
        try {
            resourcesApi.createResourceWithDefaults(SamConstants.RESOURCE_TYPE, encodedPath);

            final AccessPolicyMembership writerPolicy = new AccessPolicyMembership();
            writerPolicy.addRolesItem("writer");
            resourcesApi.overwritePolicy(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.WRITE_POLICY, writerPolicy);

            final AccessPolicyMembership readerPolicy = new AccessPolicyMembership();
            readerPolicy.addRolesItem("reader");
            resourcesApi.overwritePolicy(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.READ_POLICY, readerPolicy);
        } catch (ApiException e) {
            throw new CustomWebApplicationException("Error initializing permissions", e.getCode());
        }
    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
        ResourcesApi resourcesApi = getResourcesApi(user);
        String encodedPath = encodedWorkflowResource(workflow, resourcesApi.getApiClient());
        try {
            return resourcesApi.resourceAction(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.toSamAction(action));
        } catch (ApiException e) {
            return false;
        }
    }

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
        }).orElseThrow(() -> new CustomWebApplicationException("Unauthorized", HttpStatus.SC_UNAUTHORIZED));
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

    /**
     * Converts the response from SAM into a list of Dockstore {@link Permission}
     *
     * @param accessPolicyList
     * @return
     */
    List<Permission> accessPolicyResponseEntryToUserPermissions(List<AccessPolicyResponseEntry> accessPolicyList) {
        final List<Permission> permissionList = accessPolicyList.stream().map(accessPolicy -> {
            Role role = samPermissionMap.get(accessPolicy.getPolicy().getRoles().get(0));
            return accessPolicy.getPolicy().getMemberEmails().stream().map(email -> {
                Permission permission = new Permission();
                permission.setRole(role);
                permission.setEmail(email);
                return permission;
            });
        }).flatMap(s -> s).collect(Collectors.toList());
        return removeDuplicateEmails(permissionList);

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
    private List<Permission> removeDuplicateEmails(List<Permission> permissionList) {
        // A map of email to permissions.
        final Map<String, Permission> map = new HashMap<>();
        permissionList.stream().forEach(permission -> {
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
