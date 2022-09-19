package io.dockstore.webservice.permissions.sam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.User.Profile;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.permissions.Permission;
import io.dockstore.webservice.permissions.Role;
import io.swagger.sam.client.ApiClient;
import io.swagger.sam.client.ApiException;
import io.swagger.sam.client.api.ResourcesApi;
import io.swagger.sam.client.model.AccessPolicyMembership;
import io.swagger.sam.client.model.AccessPolicyResponseEntry;
import io.swagger.sam.client.model.ErrorReport;
import io.swagger.sam.client.model.ResourceAndAccessPolicy;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class SamPermissionsImplTest {

    private static final String FOO_WORKFLOW_NAME = "foo";
    private static final String GOO_WORKFLOW_NAME = "goo";
    private static final String DOCKSTORE_ORG_WORKFLOW_NAME = "dockstore.org/john/myworkflow";
    private static final String JANE_DOE_GMAIL_COM = "jane.doe@gmail.com";
    private static final String JOHN_SMITH_GMAIL_COM = "john.smith@gmail.com";
    private static final String JANE_DOE_GITHUB_ID = "jdoe";
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };
    private AccessPolicyResponseEntry ownerPolicy;
    private AccessPolicyResponseEntry writerPolicy;
    private SamPermissionsImpl samPermissionsImpl;
    private User janeDoeUserMock = Mockito.mock(User.class);
    private User johnSmithUserMock = Mockito.mock(User.class);
    private User noGoogleUser = Mockito.mock(User.class);
    /**
     * A workflow mock with <code>janeDoeUserMock</code> as its user
     */
    private ResourcesApi resourcesApiMock;
    /**
     * A workflow that has one user at start, <code>janeDoeUserMock</code>.
     */
    private Workflow workflowInstance;
    private Permission ownerPermission;
    private Permission writerPermission;
    private Permission readerPermission;
    private AccessPolicyResponseEntry readerAccessPolicyResponseEntry;

    @Before
    public void setup() {
        ownerPolicy = new AccessPolicyResponseEntry();
        ownerPolicy.setPolicyName(SamConstants.OWNER_POLICY);
        AccessPolicyMembership accessPolicyMembership = new AccessPolicyMembership();
        accessPolicyMembership.setRoles(Collections.singletonList(SamConstants.OWNER_POLICY));
        accessPolicyMembership.setMemberEmails(Collections.singletonList("jdoe@ucsc.edu"));
        ownerPolicy.setPolicy(accessPolicyMembership);

        writerPolicy = new AccessPolicyResponseEntry();
        writerPolicy.setPolicyName(SamConstants.WRITE_POLICY);
        AccessPolicyMembership writerMembership = new AccessPolicyMembership();
        writerMembership.setRoles(Collections.singletonList(SamConstants.WRITE_POLICY));
        writerMembership.setMemberEmails(Collections.singletonList(JOHN_SMITH_GMAIL_COM));
        writerPolicy.setPolicy(writerMembership);

        AccessPolicyResponseEntry readerPolicy = new AccessPolicyResponseEntry();
        readerPolicy.setPolicyName(SamConstants.READ_POLICY);
        AccessPolicyMembership readerMembership = new AccessPolicyMembership();
        readerMembership.setRoles(Collections.singletonList(SamConstants.READ_POLICY));
        readerMembership.setMemberEmails(Collections.singletonList(JOHN_SMITH_GMAIL_COM));
        readerPolicy.setPolicy(readerMembership);

        TokenDAO tokenDAO = Mockito.mock(TokenDAO.class);
        DockstoreWebserviceConfiguration configMock = Mockito.mock(DockstoreWebserviceConfiguration.class);
        when(configMock.getSamConfiguration()).thenReturn(new DockstoreWebserviceConfiguration.SamConfiguration());
        samPermissionsImpl = Mockito.spy(new SamPermissionsImpl(tokenDAO, configMock));
        doReturn(Optional.of("my token")).when(samPermissionsImpl).googleAccessToken(
            janeDoeUserMock);
        doReturn(Mockito.mock(Token.class)).when(samPermissionsImpl).googleToken(janeDoeUserMock);
        doReturn(Optional.of("my token")).when(samPermissionsImpl).googleAccessToken(
            johnSmithUserMock);
        doReturn(Mockito.mock(Token.class)).when(samPermissionsImpl).googleToken(johnSmithUserMock);
        resourcesApiMock = Mockito.mock(ResourcesApi.class);
        ApiClient apiClient = Mockito.mock(ApiClient.class);
        when(apiClient.escapeString(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(resourcesApiMock.getApiClient()).thenReturn(apiClient);
        when(samPermissionsImpl.getResourcesApi(janeDoeUserMock)).thenReturn(resourcesApiMock);
        when(samPermissionsImpl.getResourcesApi(johnSmithUserMock)).thenReturn(resourcesApiMock);

        workflowInstance = Mockito.mock(Workflow.class);
        when(workflowInstance.getWorkflowPath()).thenReturn("foo");
        when(workflowInstance.getUsers()).thenReturn(Set.of(janeDoeUserMock));

        ownerPermission = new Permission();
        ownerPermission.setEmail("jdoe@ucsc.edu");
        ownerPermission.setRole(Role.OWNER);
        Assert.assertThat(samPermissionsImpl
                        .accessPolicyResponseEntryToUserPermissions(Collections.singletonList(ownerPolicy)),
                CoreMatchers.is(Collections.singletonList(ownerPermission)));

        writerPermission = new Permission();
        writerPermission.setEmail(JOHN_SMITH_GMAIL_COM);
        writerPermission.setRole(Role.WRITER);

        readerPermission = new Permission();
        readerPermission.setEmail(JOHN_SMITH_GMAIL_COM);
        readerPermission.setRole(Role.READER);

        AccessPolicyMembership readerAccessPolicyMembership = new AccessPolicyMembership();
        readerAccessPolicyResponseEntry = new AccessPolicyResponseEntry();
        readerAccessPolicyResponseEntry.setPolicy(readerAccessPolicyMembership);
        readerAccessPolicyResponseEntry.setPolicyName(SamConstants.READ_POLICY);
        readerAccessPolicyResponseEntry.getPolicy().addRolesItem(SamConstants.READ_POLICY);
        readerAccessPolicyResponseEntry.getPolicy().addMemberEmailsItem(JANE_DOE_GMAIL_COM);

        final User.Profile profile = new User.Profile();
        profile.email = JANE_DOE_GMAIL_COM;
        final Map<String, User.Profile> map = new HashMap<>();
        map.put(TokenType.GOOGLE_COM.toString(), profile);
        when(janeDoeUserMock.getUserProfiles()).thenReturn(map);

        final Profile johnSmithUserProfile = new Profile();
        johnSmithUserProfile.email = JOHN_SMITH_GMAIL_COM;
        when(johnSmithUserMock.getUserProfiles())
            .thenReturn(Map.of(TokenType.GOOGLE_COM.toString(), johnSmithUserProfile));
    }

    @Test
    public void testAccessPolicyResponseEntryToUserPermissions() {
        final List<Permission> permissions = samPermissionsImpl
                .accessPolicyResponseEntryToUserPermissions(Arrays.asList(ownerPolicy, writerPolicy));
        Assert.assertEquals(permissions.size(), 2);

        Assert.assertTrue(permissions.contains(ownerPermission));
        Assert.assertTrue(permissions.contains(writerPermission));
    }

    @Test
    public void testRemoveDuplicateEmails() {
        // If a user belongs to two different roles, which the UI does not support, just return
        // the most powerful role.
        final List<Permission> permissions = samPermissionsImpl
                .removeDuplicateEmails(Arrays.asList(ownerPermission, writerPermission, readerPermission));
        Assert.assertEquals(
                permissions.size(), 2);
        Assert.assertTrue(permissions.contains(ownerPermission));
        Assert.assertTrue(permissions.contains(writerPermission));
        Assert.assertFalse(permissions.contains(readerPermission));
    }

    @Test
    public void testReadValue() {
        String response = "{\n" + "\"statusCode\": 400,\n" + "\"source\": \"sam\",\n" + "\"causes\": [],\n" + "\"stackTrace\": [],\n"
            + "\"message\": \"jane_doe@yahoo.com not found\"\n" + "}";
        Optional<ErrorReport> errorReport = samPermissionsImpl.readValue(response, ErrorReport.class);
        Assert.assertEquals(errorReport.get().getMessage(), "jane_doe@yahoo.com not found");

        Assert.assertFalse(samPermissionsImpl.readValue((String)null, ErrorReport.class).isPresent());
    }

    @Test
    public void testWorkflowsSharedWithUser() throws ApiException, UnsupportedEncodingException {
        ResourceAndAccessPolicy reader = new ResourceAndAccessPolicy();
        reader.setResourceId(SamConstants.ENCODED_WORKFLOW_PREFIX + FOO_WORKFLOW_NAME);
        reader.setAccessPolicyName(SamConstants.READ_POLICY);
        ResourceAndAccessPolicy owner = new ResourceAndAccessPolicy();
        owner.setResourceId(SamConstants.ENCODED_WORKFLOW_PREFIX + GOO_WORKFLOW_NAME);
        owner.setAccessPolicyName(SamConstants.OWNER_POLICY);
        ResourceAndAccessPolicy writer = new ResourceAndAccessPolicy();
        writer.setResourceId(SamConstants.ENCODED_WORKFLOW_PREFIX + URLEncoder.encode(DOCKSTORE_ORG_WORKFLOW_NAME, StandardCharsets.UTF_8));
        writer.setAccessPolicyName(SamConstants.WRITE_POLICY);
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE)).thenReturn(Arrays.asList(reader, owner, writer));
        final Map<Role, List<String>> sharedWithUser = samPermissionsImpl.workflowsSharedWithUser(
            janeDoeUserMock);
        Assert.assertEquals(3, sharedWithUser.size());
        final List<String> ownerWorkflows = sharedWithUser.get(Role.OWNER);
        Assert.assertEquals(GOO_WORKFLOW_NAME, ownerWorkflows.get(0));
        final List<String> readerWorkflows = sharedWithUser.get(Role.READER);
        Assert.assertEquals(FOO_WORKFLOW_NAME, readerWorkflows.get(0));
        final List<String> writerWorkflows = sharedWithUser.get(Role.WRITER);
        Assert.assertEquals(DOCKSTORE_ORG_WORKFLOW_NAME, writerWorkflows.get(0));
    }

    @Test
    public void testCanRead() throws ApiException {
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME, Role.Action.READ.toString())).thenReturn(Boolean.TRUE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + GOO_WORKFLOW_NAME, Role.Action.READ.toString())).thenReturn(Boolean.FALSE);
        when(samPermissionsImpl.getResourcesApi(janeDoeUserMock)).thenReturn(resourcesApiMock);

        Workflow fooWorkflow = Mockito.mock(Workflow.class);
        when(fooWorkflow.getWorkflowPath()).thenReturn(FOO_WORKFLOW_NAME, GOO_WORKFLOW_NAME);
        Assert.assertTrue(samPermissionsImpl.canDoAction(janeDoeUserMock, fooWorkflow, Role.Action.READ));
        Workflow gooWorkflow = Mockito.mock(Workflow.class);
        Assert.assertFalse(samPermissionsImpl.canDoAction(janeDoeUserMock, gooWorkflow, Role.Action.READ));
    }

    @Test
    public void testCanWrite() throws ApiException {
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME, Role.Action.WRITE.toString())).thenReturn(Boolean.TRUE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + GOO_WORKFLOW_NAME, Role.Action.WRITE.toString())).thenReturn(Boolean.FALSE);
        when(samPermissionsImpl.getResourcesApi(janeDoeUserMock)).thenReturn(resourcesApiMock);

        when(workflowInstance.getWorkflowPath()).thenReturn(FOO_WORKFLOW_NAME);
        Assert.assertTrue(samPermissionsImpl.canDoAction(janeDoeUserMock, workflowInstance, Role.Action.WRITE));
        Workflow gooWorkflow = Mockito.mock(Workflow.class);
        when(gooWorkflow.getWorkflowPath()).thenReturn(GOO_WORKFLOW_NAME);
        Assert.assertFalse(samPermissionsImpl.canDoAction(janeDoeUserMock, gooWorkflow, Role.Action.WRITE));
    }

    @Test
    public void testSetPermission() throws ApiException {
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME))
                .thenReturn(Collections.emptyList(), Collections.singletonList(readerAccessPolicyResponseEntry));
        Permission permission = new Permission();
        permission.setEmail(JOHN_SMITH_GMAIL_COM);
        permission.setRole(Role.READER);
        List<Permission> permissions = samPermissionsImpl.setPermission(janeDoeUserMock, workflowInstance, permission);
        Assert.assertEquals(permissions.size(), 1);
    }

    @Test
    public void setPermissionTest1() {
        try {
            when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME)).thenThrow(new ApiException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Server error"));
            final Permission permission = new Permission();
            permission.setEmail("jdoe@example.com");
            permission.setRole(Role.WRITER);
            thrown.expect(CustomWebApplicationException.class);
            samPermissionsImpl.setPermission(janeDoeUserMock, workflowInstance, permission);
            fail("setPermissions did not throw Exception");
        } catch (ApiException e) {
            fail();
            e.printStackTrace();
        }
    }

    @Test
    public void setPermissionTest2() {
        try {
            final Permission permission = new Permission();
            permission.setEmail(JOHN_SMITH_GMAIL_COM);
            permission.setRole(Role.READER);
            when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME))
                    .thenThrow(new ApiException(404, "Not Found"))
                    .thenReturn(Collections.singletonList(readerAccessPolicyResponseEntry));
            try {
                setupInitializePermissionsMocks(SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME);
                final List<Permission> permissions = samPermissionsImpl.setPermission(
                    janeDoeUserMock, workflowInstance, permission);
                Assert.assertEquals(permissions.size(), 1);
            } catch (CustomWebApplicationException ex) {
                fail("setPermissions threw Exception");
            }
        } catch (ApiException e) {
            fail();
            e.printStackTrace();
        }
    }

    /**
     * Verifies that if a user already has a permission on a workflow, and we then set a different permission, that the
     * call to remove the old permission is made.
     *
     * @throws ApiException
     */
    @Test
    public void setPermissionRemovesExistingPermission() throws ApiException {
        final Permission localReaderPermission = new Permission(JOHN_SMITH_GMAIL_COM, Role.READER);
        final String encodedPath = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, encodedPath))
                .thenReturn(Collections.singletonList(writerPolicy));
        setupInitializePermissionsMocks(encodedPath);
        doNothing().when(resourcesApiMock)
                .removeUserFromPolicy(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.READ_POLICY, JOHN_SMITH_GMAIL_COM);
        samPermissionsImpl.setPermission(janeDoeUserMock, workflowInstance, localReaderPermission);
        verify(resourcesApiMock, times(1))
                .removeUserFromPolicy(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.WRITE_POLICY, JOHN_SMITH_GMAIL_COM);
    }



    @Test
    public void removePermissionTest() throws ApiException {
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX  + FOO_WORKFLOW_NAME))
                .thenReturn(Collections.singletonList(readerAccessPolicyResponseEntry),
                    Collections.singletonList(readerAccessPolicyResponseEntry), Collections.EMPTY_LIST);
        final List<Permission> permissions = samPermissionsImpl.getPermissionsForWorkflow(
            janeDoeUserMock, workflowInstance);
        Assert.assertEquals(permissions.size(), 1);
        try {
            samPermissionsImpl.removePermission(janeDoeUserMock, workflowInstance, JOHN_SMITH_GMAIL_COM, Role.READER);
        } catch (CustomWebApplicationException e) {
            fail();
        }
    }

    @Test
    public void userNotInSamReturnsEmptyMap() throws ApiException {
        // https://github.com/dockstore/dockstore/issues/1597
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE)).thenThrow(new ApiException(HttpStatus.SC_UNAUTHORIZED, "Unauthorized"));
        final Map<Role, List<String>> sharedWithUser = samPermissionsImpl.workflowsSharedWithUser(
            janeDoeUserMock);
        Assert.assertEquals(0, sharedWithUser.size());
    }

    @Test
    public void testOwnersActions() throws ApiException {
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.SHARE)))
                .thenReturn(Boolean.TRUE);
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(janeDoeUserMock, workflowInstance);
        Assert.assertEquals(Role.Action.values().length, actions.size()); // Owner can perform all actions
    }

    @Test
    public void testWritersActions() throws ApiException {
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.SHARE)))
                .thenReturn(Boolean.FALSE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.WRITE)))
                .thenReturn(Boolean.TRUE);
        when(johnSmithUserMock.getTemporaryCredential()).thenReturn("whatever");
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(johnSmithUserMock, workflowInstance);
        Assert.assertEquals(2, actions.size());
        Assert.assertTrue(actions.contains(Role.Action.WRITE) && actions.contains(Role.Action.READ));
    }

    @Test
    public void testReadersActions() throws ApiException {
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.SHARE)))
                .thenReturn(Boolean.FALSE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.WRITE)))
                .thenReturn(Boolean.FALSE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.READ)))
                .thenReturn(Boolean.TRUE);
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(
            johnSmithUserMock, workflowInstance);
        Assert.assertEquals(1, actions.size());
        Assert.assertTrue(actions.contains(Role.Action.READ));
    }

    @Test
    public void testDockstoreOwnerNoSamPermissions() {
        when(workflowInstance.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(
            janeDoeUserMock)));
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(janeDoeUserMock, workflowInstance);
        Assert.assertEquals(Role.Action.values().length, actions.size()); // Owner can perform all actions
    }

    /**
     * Test that a user with no permissions at all gets an exception
     * @throws ApiException
     */
    @Test
    public void testNoPermissions() throws ApiException {
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, resourceId))
                .thenThrow(new ApiException(HttpStatus.SC_FORBIDDEN, "Unauthorized"));
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.READ)))
                .thenReturn(Boolean.FALSE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.WRITE)))
                .thenReturn(Boolean.FALSE);
        thrown.expect(CustomWebApplicationException.class);
        samPermissionsImpl.getPermissionsForWorkflow(janeDoeUserMock, workflowInstance);
    }

    @Test
    public void testUserInTwoPoliciesForSameResource() throws ApiException {
        // https://github.com/dockstore/dockstore/issues/1609, second item
        final String resourceId = SamConstants.ENCODED_WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        ResourceAndAccessPolicy reader = new ResourceAndAccessPolicy();
        reader.setResourceId(resourceId);
        reader.setAccessPolicyName(SamConstants.READ_POLICY);
        ResourceAndAccessPolicy writer = new ResourceAndAccessPolicy();
        writer.setResourceId(resourceId);
        writer.setAccessPolicyName(SamConstants.WRITE_POLICY);
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE))
                .thenReturn(Arrays.asList(reader, writer))
                .thenReturn(Arrays.asList(writer, reader));

        // Should be 1 workflow, with the writer role, because writer > reader
        final Map<Role, List<String>> sharedWithUser = samPermissionsImpl.workflowsSharedWithUser(
            janeDoeUserMock);
        Assert.assertEquals(1, sharedWithUser.size());
        Assert.assertEquals(Role.WRITER, sharedWithUser.entrySet().iterator().next().getKey());

        // Verify it works the same if the SAM API returns the policies in a different order.
        final Map<Role, List<String>> sharedWithUser2 = samPermissionsImpl.workflowsSharedWithUser(
            janeDoeUserMock);
        Assert.assertEquals(1, sharedWithUser.size());
        Assert.assertEquals(Role.WRITER, sharedWithUser.entrySet().iterator().next().getKey());
    }

    @Test
    public void testNotOriginalOwnerWithEmailNotUsername() {
        when(janeDoeUserMock.getUsername()).thenReturn(JANE_DOE_GITHUB_ID);
        when(workflowInstance.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(
            janeDoeUserMock)));
        thrown.expect(CustomWebApplicationException.class);
        samPermissionsImpl.checkEmailNotOriginalOwner(JANE_DOE_GMAIL_COM, workflowInstance);
        samPermissionsImpl.checkEmailNotOriginalOwner("johndoe@example.com", workflowInstance);
    }

    @Test
    public void testIsSharingNotInSam() throws ApiException {
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE))
                .thenThrow(new ApiException(HttpStatus.SC_UNAUTHORIZED, "Unauthorized"));
        Assert.assertFalse(samPermissionsImpl.isSharing(janeDoeUserMock));
    }

    @Test
    public void testIsSharingUserInSam() throws ApiException {
        final String resourceId = SamConstants.ENCODED_WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        ResourceAndAccessPolicy reader = resourceAndAccessPolicyHelper(SamConstants.READ_POLICY);
        ResourceAndAccessPolicy writer = resourceAndAccessPolicyHelper(SamConstants.WRITE_POLICY);
        ResourceAndAccessPolicy owner = resourceAndAccessPolicyHelper(SamConstants.OWNER_POLICY);

        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE))
                .thenReturn(Collections.emptyList()) // case 1
                .thenReturn(Arrays.asList(reader, writer)) // case 2
                .thenReturn(Collections.singletonList(owner)) // case 3
                .thenReturn(Collections.singletonList(owner)); // case 4
        // Case 1: No resources are shared
        Assert.assertFalse(samPermissionsImpl.isSharing(janeDoeUserMock));

        // Case 2: Being shared with, but not sharing
        Assert.assertFalse(samPermissionsImpl.isSharing(janeDoeUserMock));

        // Case 3: Is owner, but not shared with anybody
        AccessPolicyResponseEntry onlyOwner = new AccessPolicyResponseEntry();
        onlyOwner.setPolicy(new AccessPolicyMembership());
        onlyOwner.setPolicyName(SamConstants.OWNER_POLICY);
        onlyOwner.getPolicy().getMemberEmails().add(JANE_DOE_GMAIL_COM);
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, resourceId))
                .thenReturn(Collections.singletonList(onlyOwner));
        Assert.assertFalse(samPermissionsImpl.isSharing(janeDoeUserMock));

        // Case 4: Is owner, and is being shared
        onlyOwner.getPolicy().getMemberEmails().add("jdoe@ucsc.edu");
        Assert.assertTrue(samPermissionsImpl.isSharing(janeDoeUserMock));
    }

    @Test
    public void testSelfDestruct() throws ApiException {
        // Case 0. User doesn't have Google token
        samPermissionsImpl.selfDestruct(noGoogleUser);

        final String resourceId = SamConstants.ENCODED_WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        ResourceAndAccessPolicy reader = resourceAndAccessPolicyHelper(SamConstants.READ_POLICY);
        ResourceAndAccessPolicy writer = resourceAndAccessPolicyHelper(SamConstants.WRITE_POLICY);
        ResourceAndAccessPolicy owner = resourceAndAccessPolicyHelper(SamConstants.OWNER_POLICY);

        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE))
                .thenThrow(new ApiException(HttpStatus.SC_UNAUTHORIZED, "Unauthorized")) // case 1
                .thenReturn(Collections.emptyList()) // case 2
                .thenReturn(Arrays.asList(reader, writer)) // case 3
                .thenReturn(Collections.singletonList(owner)) // case 4
                .thenReturn(Collections.singletonList(owner)); // case 5

        // Case 1. User has Google token, but is not in SAM
        samPermissionsImpl.selfDestruct(janeDoeUserMock);

        // Case 2. User has Google token, in SAM, has no resources
        samPermissionsImpl.selfDestruct(janeDoeUserMock);

        // Case 3. User is not owner, but has workflows shared with
        samPermissionsImpl.selfDestruct(janeDoeUserMock);

        // Case 4. User is owner, but is not sharing with anybody
        AccessPolicyResponseEntry onlyOwner = new AccessPolicyResponseEntry();
        onlyOwner.setPolicy(new AccessPolicyMembership());
        onlyOwner.setPolicyName(SamConstants.OWNER_POLICY);
        onlyOwner.getPolicy().getMemberEmails().add(JANE_DOE_GMAIL_COM);
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, resourceId))
                .thenReturn(Collections.singletonList(onlyOwner));
        samPermissionsImpl.selfDestruct(janeDoeUserMock);

        // Case 5. User is owner and has shared
        onlyOwner.getPolicy().getMemberEmails().add("jdoe@ucsc.edu");
        thrown.expect(CustomWebApplicationException.class);
        samPermissionsImpl.selfDestruct(janeDoeUserMock);

    }

    /**
     * Tests this use case:
     *
     * <ol>
     *     <li>User creates a SAM resource with the SAM API directly. When you do that, the writer and reader policies are
     *     not added.</li>
     *     <li>Using Dockstore API, user then adds an email to Writer role.</li>
     * </ol>
     *
     * This tests that the writer policy gets added to the resource.
     * @throws ApiException
     */
    @Test
    public void testSetPermissionWhenResourceCreatedWithoutAllPolicies() throws ApiException {
        when(workflowInstance.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(
            janeDoeUserMock)));
        final String resourceId = SamConstants.ENCODED_WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        ResourceAndAccessPolicy owner = resourceAndAccessPolicyHelper(SamConstants.OWNER_POLICY);
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE))
                .thenReturn(Collections.singletonList(owner));
        setupInitializePermissionsMocks(resourceId);
        samPermissionsImpl.setPermission(
            janeDoeUserMock, workflowInstance, new Permission("johndoe@example.com", Role.WRITER));
        verify(resourcesApiMock, times(1)).overwritePolicy(eq(SamConstants.RESOURCE_TYPE), anyString(), eq(SamConstants.WRITE_POLICY), any());
    }

    @Test
    public void testSamPolicyOwnedbyAnother() throws ApiException {
        when(workflowInstance.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(
            janeDoeUserMock)));
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, resourceId))
                .thenThrow(new ApiException(HttpStatus.SC_NOT_FOUND, "")); // If you don't have permissions, you get a 404
        doThrow(new ApiException(HttpStatus.SC_CONFLICT, ""))
                .when(resourcesApiMock)
                .createResourceWithDefaults(SamConstants.RESOURCE_TYPE, resourceId);
        try {
            samPermissionsImpl.setPermission(
                janeDoeUserMock, workflowInstance, new Permission("johndoe@example.com", Role.WRITER));
            fail("Expected setPermission to throw exception");
        } catch (CustomWebApplicationException ex) {
            Assert.assertTrue(ex.getMessage().contains(" 409 "));
        }
    }

    @Test
    public void testAddingOwnerDoesNotCreateSecondOwnerPolicy() throws ApiException { //https://github.com/dockstore/dockstore/issues/1805
        when(workflowInstance.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(
            janeDoeUserMock)));
        ResourceAndAccessPolicy owner = resourceAndAccessPolicyHelper(SamConstants.OWNER_POLICY);
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE))
                .thenReturn(Collections.singletonList(owner));
        samPermissionsImpl.setPermission(
            janeDoeUserMock, workflowInstance, new Permission("johndoe@example.com", Role.OWNER));
        verify(resourcesApiMock, times(0)).overwritePolicy(eq(SamConstants.RESOURCE_TYPE), anyString(), eq(SamConstants.OWNER_POLICY), any());
    }

    @Test
    public void testOwnerCanGetPermissions() {
        final List<Permission> permissions =
            samPermissionsImpl.getPermissionsForWorkflow(janeDoeUserMock, workflowInstance);
        assertEquals(1, permissions.size());
        final Permission permission = permissions.get(0);
        assertEquals(JANE_DOE_GMAIL_COM, permission.getEmail());
        assertEquals(Role.OWNER, permission.getRole());
    }

    @Test
    public void testNonOwnerCannotGetPermissions() {
        try {
            samPermissionsImpl.getPermissionsForWorkflow(johnSmithUserMock, workflowInstance);
            fail("Non-owner shouldn't be able to get permissions");
        } catch (CustomWebApplicationException e) {
            assertEquals("Forbidden", e.getErrorMessage());
        }
    }

    private void setupInitializePermissionsMocks(String encodedPath) {
        try {
            doNothing().when(resourcesApiMock).createResourceWithDefaults(anyString(), anyString());
            doNothing().when(resourcesApiMock).overwritePolicy(anyString(), anyString(), anyString(), any());
        } catch (ApiException e) {
            fail();
        }
    }

    private ResourceAndAccessPolicy resourceAndAccessPolicyHelper(String policyName) {
        final ResourceAndAccessPolicy policy = new ResourceAndAccessPolicy();
        policy.setResourceId("%23workflow%2Ffoo");
        policy.setAccessPolicyName(policyName);
        return policy;
    }

}
