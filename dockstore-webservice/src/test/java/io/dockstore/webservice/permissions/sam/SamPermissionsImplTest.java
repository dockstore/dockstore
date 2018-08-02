package io.dockstore.webservice.permissions.sam;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
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
import org.apache.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SamPermissionsImplTest {

    public static final String FOO_WORKFLOW_NAME = "foo";
    public static final String GOO_WORKFLOW_NAME = "goo";
    public static final String DOCKSTORE_ORG_WORKFLOW_NAME = "dockstore.org/john/myworkflow";
    public static final String JANE_DOE_GMAIL_COM = "jane.doe@gmail.com";
    public static final String JANE_DOE_GITHUB_ID = "jdoe";
    private AccessPolicyResponseEntry ownerPolicy;
    private AccessPolicyResponseEntry writerPolicy;
    private AccessPolicyResponseEntry readerPolicy;
    private SamPermissionsImpl samPermissionsImpl;
    private User userMock = Mockito.mock(User.class);
    private ResourcesApi resourcesApiMock;
    private ApiClient apiClient;
    private Workflow fooWorkflow;
    private Permission ownerPermission;
    private Permission writerPermission;
    private Permission readerPermission;
    private AccessPolicyMembership readerAccessPolicyMemebership;
    private AccessPolicyResponseEntry readerAccessPolicyResponseEntry;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        ownerPolicy = new AccessPolicyResponseEntry();
        ownerPolicy.setPolicyName(SamConstants.OWNER_POLICY);
        AccessPolicyMembership accessPolicyMembership = new AccessPolicyMembership();
        accessPolicyMembership.setRoles(Arrays.asList(SamConstants.OWNER_ROLE));
        accessPolicyMembership.setMemberEmails(Arrays.asList("jdoe@ucsc.edu"));
        ownerPolicy.setPolicy(accessPolicyMembership);

        writerPolicy = new AccessPolicyResponseEntry();
        writerPolicy.setPolicyName(SamConstants.WRITE_POLICY);
        AccessPolicyMembership writerMembership = new AccessPolicyMembership();
        writerMembership.setRoles(Arrays.asList(SamConstants.WRITE_ROLE));
        writerMembership.setMemberEmails(Arrays.asList(JANE_DOE_GMAIL_COM));
        writerPolicy.setPolicy(writerMembership);

        readerPolicy = new AccessPolicyResponseEntry();
        readerPolicy.setPolicyName(SamConstants.READ_POLICY);
        AccessPolicyMembership readerMembership = new AccessPolicyMembership();
        readerMembership.setRoles(Arrays.asList(SamConstants.READ_ROLE));
        readerMembership.setMemberEmails(Arrays.asList(JANE_DOE_GMAIL_COM));
        readerPolicy.setPolicy(readerMembership);

        TokenDAO tokenDAO = Mockito.mock(TokenDAO.class);
        DockstoreWebserviceConfiguration configMock = Mockito.mock(DockstoreWebserviceConfiguration.class);
        when(configMock.getSamConfiguration()).thenReturn(new DockstoreWebserviceConfiguration.SamConfiguration());
        samPermissionsImpl = Mockito.spy(new SamPermissionsImpl(tokenDAO, configMock));
        doReturn(Optional.of("my token")).when(samPermissionsImpl).googleAccessToken(userMock);
        doReturn(Mockito.mock(Token.class)).when(samPermissionsImpl).googleToken(userMock);
        resourcesApiMock = Mockito.mock(ResourcesApi.class);
        apiClient = Mockito.mock(ApiClient.class);
        when(apiClient.escapeString(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(resourcesApiMock.getApiClient()).thenReturn(apiClient);
        when(samPermissionsImpl.getResourcesApi(userMock)).thenReturn(resourcesApiMock);

        fooWorkflow = Mockito.mock(Workflow.class);
        when(fooWorkflow.getWorkflowPath()).thenReturn("foo");

        ownerPermission = new Permission();
        ownerPermission.setEmail("jdoe@ucsc.edu");
        ownerPermission.setRole(Role.OWNER);
        Assert.assertThat(samPermissionsImpl
                        .accessPolicyResponseEntryToUserPermissions(Arrays.asList(ownerPolicy)),
                CoreMatchers.is(Arrays.asList(ownerPermission)));

        writerPermission = new Permission();
        writerPermission.setEmail(JANE_DOE_GMAIL_COM);
        writerPermission.setRole(Role.WRITER);

        readerPermission = new Permission();
        readerPermission.setEmail(JANE_DOE_GMAIL_COM);
        readerPermission.setRole(Role.READER);

        readerAccessPolicyMemebership = new AccessPolicyMembership();
        readerAccessPolicyResponseEntry = new AccessPolicyResponseEntry();
        readerAccessPolicyResponseEntry.setPolicy(readerAccessPolicyMemebership);
        readerAccessPolicyResponseEntry.setPolicyName(SamConstants.READ_POLICY);
        readerAccessPolicyResponseEntry.getPolicy().addRolesItem(SamConstants.READ_POLICY);
        readerAccessPolicyResponseEntry.getPolicy().addMemberEmailsItem(JANE_DOE_GMAIL_COM);

        final User.Profile profile = new User.Profile();
        profile.email = JANE_DOE_GMAIL_COM;
        final HashMap<String, User.Profile> map = new HashMap<>();
        map.put(TokenType.GOOGLE_COM.toString(), profile);
        when(userMock.getUserProfiles()).thenReturn(map);

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
        String response = "{\n" +
                "\"statusCode\": 400,\n" +
                "\"source\": \"sam\",\n" +
                "\"causes\": [],\n" +
                "\"stackTrace\": [],\n" +
                "\"message\": \"jane_doe@yahoo.com not found\"\n" +
                "}";
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
        writer.setResourceId(SamConstants.ENCODED_WORKFLOW_PREFIX + URLEncoder.encode(DOCKSTORE_ORG_WORKFLOW_NAME, "UTF-8"));
        writer.setAccessPolicyName(SamConstants.WRITE_POLICY);
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE)).thenReturn(Arrays.asList(reader, owner, writer));
        final Map<Role, List<String>> sharedWithUser = samPermissionsImpl.workflowsSharedWithUser(userMock);
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
        when(samPermissionsImpl.getResourcesApi(userMock)).thenReturn(resourcesApiMock);

        Workflow fooWorkflow = Mockito.mock(Workflow.class);
        when(fooWorkflow.getWorkflowPath()).thenReturn(FOO_WORKFLOW_NAME, GOO_WORKFLOW_NAME);
        Assert.assertTrue(samPermissionsImpl.canDoAction(userMock, fooWorkflow, Role.Action.READ));
        Workflow gooWorkflow = Mockito.mock(Workflow.class);
        Assert.assertFalse(samPermissionsImpl.canDoAction(userMock, gooWorkflow, Role.Action.READ));
    }

    @Test
    public void testCanWrite() throws ApiException {
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME, Role.Action.WRITE.toString())).thenReturn(Boolean.TRUE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + GOO_WORKFLOW_NAME, Role.Action.WRITE.toString())).thenReturn(Boolean.FALSE);
        when(samPermissionsImpl.getResourcesApi(userMock)).thenReturn(resourcesApiMock);

        when(fooWorkflow.getWorkflowPath()).thenReturn(FOO_WORKFLOW_NAME);
        Assert.assertTrue(samPermissionsImpl.canDoAction(userMock, fooWorkflow, Role.Action.WRITE));
        Workflow gooWorkflow = Mockito.mock(Workflow.class);
        when(gooWorkflow.getWorkflowPath()).thenReturn(GOO_WORKFLOW_NAME);
        Assert.assertFalse(samPermissionsImpl.canDoAction(userMock, gooWorkflow, Role.Action.WRITE));
    }

    @Test
    public void testSetPermission() throws ApiException {
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME))
                .thenReturn(Collections.emptyList(), Arrays.asList(readerAccessPolicyResponseEntry));
        Permission permission = new Permission();
        permission.setEmail(JANE_DOE_GMAIL_COM);
        permission.setRole(Role.READER);
        List<Permission> permissions = samPermissionsImpl.setPermission(userMock, fooWorkflow, permission);
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
            samPermissionsImpl.setPermission(userMock, fooWorkflow, permission);
            Assert.fail("setPermissions did not throw Exception");
        } catch (ApiException e) {
            Assert.fail();
            e.printStackTrace();
        }
    }

    @Test
    public void setPermissionTest2() {
        try {
            final Permission permission = new Permission();
            permission.setEmail(JANE_DOE_GMAIL_COM);
            permission.setRole(Role.READER);
            when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME))
                    .thenThrow(new ApiException(404, "Not Found"))
                    .thenReturn( Arrays.asList(readerAccessPolicyResponseEntry));
            try {
                setupInitializePermissionsMocks(SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME);
                final List<Permission> permissions = samPermissionsImpl.setPermission(userMock, fooWorkflow, permission);
                Assert.assertEquals(permissions.size(), 1);
            } catch (CustomWebApplicationException ex) {
                Assert.fail("setPermissions threw Exception");
            }
        } catch (ApiException e) {
            Assert.fail();
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
        final Permission readerPermission = new Permission(JANE_DOE_GMAIL_COM, Role.READER);
        final String encodedPath = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, encodedPath))
                .thenReturn(Arrays.asList(writerPolicy));
        setupInitializePermissionsMocks(encodedPath);
        doNothing().when(resourcesApiMock)
                .removeUserFromPolicy(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.WRITE_POLICY, JANE_DOE_GMAIL_COM);
        samPermissionsImpl.setPermission(userMock, fooWorkflow, readerPermission);
        verify(resourcesApiMock, times(1))
                .removeUserFromPolicy(SamConstants.RESOURCE_TYPE, encodedPath, SamConstants.WRITE_POLICY, JANE_DOE_GMAIL_COM);
    }



    @Test
    public void removePermissionTest() throws ApiException {
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX  + FOO_WORKFLOW_NAME))
                .thenReturn(Arrays.asList(readerAccessPolicyResponseEntry), Arrays.asList(readerAccessPolicyResponseEntry), Collections.EMPTY_LIST);
        final List<Permission> permissions = samPermissionsImpl.getPermissionsForWorkflow(userMock, fooWorkflow);
        Assert.assertEquals(permissions.size(), 1);
        try {
            samPermissionsImpl.removePermission(userMock, fooWorkflow, JANE_DOE_GMAIL_COM, Role.READER);
        } catch (CustomWebApplicationException e) {
            Assert.fail();
        }
    }

    @Test
    public void userNotInSamReturnsEmptyMap() throws ApiException {
        // https://github.com/ga4gh/dockstore/issues/1597
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE)).thenThrow(new ApiException(HttpStatus.SC_UNAUTHORIZED, "Unauthorized"));
        final Map<Role, List<String>> sharedWithUser = samPermissionsImpl.workflowsSharedWithUser(userMock);
        Assert.assertEquals(0, sharedWithUser.size());
    }

    @Test
    public void testOwnersActions() throws ApiException {
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.SHARE)))
                .thenReturn(Boolean.TRUE);
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(userMock, fooWorkflow);
        Assert.assertEquals(Role.Action.values().length, actions.size()); // Owner can perform all actions
    }

    @Test
    public void testWritersActions() throws ApiException {
        final String resourceId = SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME;
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.SHARE)))
                .thenReturn(Boolean.FALSE);
        when(resourcesApiMock.resourceAction(SamConstants.RESOURCE_TYPE, resourceId, SamConstants.toSamAction(Role.Action.WRITE)))
                .thenReturn(Boolean.TRUE);
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(userMock, fooWorkflow);
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
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(userMock, fooWorkflow);
        Assert.assertEquals(1, actions.size());
        Assert.assertTrue(actions.contains(Role.Action.READ));
    }

    @Test
    public void testDockstoreOwnerNoSamPermissions() {
        when(fooWorkflow.getUsers()).thenReturn(new HashSet<>(Arrays.asList(userMock)));
        final List<Role.Action> actions = samPermissionsImpl.getActionsForWorkflow(userMock, fooWorkflow);
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
        samPermissionsImpl.getPermissionsForWorkflow(userMock, fooWorkflow);
    }

    @Test
    public void testUserInTwoPoliciesForSameResource() throws ApiException {
        // https://github.com/ga4gh/dockstore/issues/1609, second item
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
        final Map<Role, List<String>> sharedWithUser = samPermissionsImpl.workflowsSharedWithUser(userMock);
        Assert.assertEquals(1, sharedWithUser.size());
        Assert.assertEquals(Role.WRITER, sharedWithUser.entrySet().iterator().next().getKey());

        // Verify it works the same if the SAM API returns the policies in a different order.
        final Map<Role, List<String>> sharedWithUser2 = samPermissionsImpl.workflowsSharedWithUser(userMock);
        Assert.assertEquals(1, sharedWithUser.size());
        Assert.assertEquals(Role.WRITER, sharedWithUser.entrySet().iterator().next().getKey());
    }

    @Test
    public void testNotOriginalOwnerWithEmailNotUsername() {
        when(userMock.getUsername()).thenReturn(JANE_DOE_GITHUB_ID);
        when(fooWorkflow.getUsers()).thenReturn(new HashSet<>(Arrays.asList(userMock)));
        thrown.expect(CustomWebApplicationException.class);
        samPermissionsImpl.checkEmailNotOriginalOwner(JANE_DOE_GMAIL_COM, fooWorkflow);
        samPermissionsImpl.checkEmailNotOriginalOwner("johndoe@example.com", fooWorkflow);
    }

    private void setupInitializePermissionsMocks(String encodedPath) {
        try {
            doNothing().when(resourcesApiMock).createResourceWithDefaults(anyString(), anyString());
            doNothing().when(resourcesApiMock).overwritePolicy(anyString(), anyString(), anyString(), any());
        } catch (ApiException e) {
            Assert.fail();
        }
    }

}
