package io.dockstore.webservice.permissions.sam;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
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
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class SamPermissionsImplTest {

    public static final String FOO_WORKFLOW_NAME = "foo";
    public static final String GOO_WORKFLOW_NAME = "goo";
    public static final String JANE_DOE_GMAIL_COM = "jane.doe@gmail.com";
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

    @Before
    public void setup() {
        ownerPolicy = new AccessPolicyResponseEntry();
        ownerPolicy.setPolicyName(SamConstants.OWNER_POLICY);
        AccessPolicyMembership accessPolicyMembership = new AccessPolicyMembership();
        accessPolicyMembership.setRoles(Arrays.asList(new String[] {SamConstants.OWNER_ROLE}));
        accessPolicyMembership.setMemberEmails(Arrays.asList(new String [] {"jdoe@ucsc.edu"}));
        ownerPolicy.setPolicy(accessPolicyMembership);

        writerPolicy = new AccessPolicyResponseEntry();
        writerPolicy.setPolicyName(SamConstants.WRITE_POLICY);
        AccessPolicyMembership writerMembership = new AccessPolicyMembership();
        writerMembership.setRoles(Arrays.asList(new String[] {SamConstants.WRITE_ROLE}));
        writerMembership.setMemberEmails(Arrays.asList(new String[] { JANE_DOE_GMAIL_COM }));
        writerPolicy.setPolicy(writerMembership);

        readerPolicy = new AccessPolicyResponseEntry();
        readerPolicy.setPolicyName(SamConstants.READ_POLICY);
        AccessPolicyMembership readerMembership = new AccessPolicyMembership();
        readerMembership.setRoles(Arrays.asList(new String[] {SamConstants.READ_ROLE}));
        readerMembership.setMemberEmails(Arrays.asList(new String[] { JANE_DOE_GMAIL_COM }));
        readerPolicy.setPolicy(readerMembership);

        TokenDAO tokenDAO = Mockito.mock(TokenDAO.class);
        DockstoreWebserviceConfiguration configMock = Mockito.mock(DockstoreWebserviceConfiguration.class);
        when(configMock.getSamConfiguration()).thenReturn(new DockstoreWebserviceConfiguration.SamConfiguration());
        samPermissionsImpl = Mockito.spy(new SamPermissionsImpl(tokenDAO, configMock));
        doReturn(Optional.of("my token")).when(samPermissionsImpl).googleAccessToken(userMock);
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
        readerAccessPolicyResponseEntry.getPolicy().addRolesItem(SamConstants.READ_POLICY.toString());
        readerAccessPolicyResponseEntry.getPolicy().addMemberEmailsItem(JANE_DOE_GMAIL_COM);

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
    public void testAccessPolicyResponseEntryToUserPermissionsDuplicateEmails() {
        // If a user belongs to two different roles, which the UI does not support, just return
        // the most powerful role.
        final List<Permission> permissions = samPermissionsImpl
                .accessPolicyResponseEntryToUserPermissions(Arrays.asList(ownerPolicy, writerPolicy, readerPolicy));
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
    public void testWorkflowsSharedWithUser() throws ApiException {
        ResourceAndAccessPolicy reader = new ResourceAndAccessPolicy();
        reader.setResourceId(SamConstants.ENCODED_WORKFLOW_PREFIX + FOO_WORKFLOW_NAME);
        reader.setAccessPolicyName(SamConstants.READ_POLICY);
        ResourceAndAccessPolicy owner = new ResourceAndAccessPolicy();
        owner.setResourceId(SamConstants.ENCODED_WORKFLOW_PREFIX + GOO_WORKFLOW_NAME);
        owner.setAccessPolicyName(SamConstants.OWNER_POLICY);
        when(resourcesApiMock.listResourcesAndPolicies(SamConstants.RESOURCE_TYPE)).thenReturn(Arrays.asList(reader, owner));
        Assert.assertEquals(samPermissionsImpl.workflowsSharedWithUser(userMock), Arrays.asList(FOO_WORKFLOW_NAME));
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
//        final String janeEmail = "jane@example.org";
//        readerAccessPolicyResponseEntry.getPolicy().addMemberEmailsItem(janeEmail);
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME))
                .thenReturn(Collections.emptyList(), Arrays.asList(readerAccessPolicyResponseEntry));
        Permission permission = new Permission();
        permission.setEmail(JANE_DOE_GMAIL_COM);
        permission.setRole(Role.READER);
        List<Permission> permissions = samPermissionsImpl.setPermission(fooWorkflow, userMock, permission);
        Assert.assertEquals(permissions.size(), 1);
    }

    @Test
    public void setPermissionTest1() {
        try {
            when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME)).thenThrow(new ApiException(500, "Server error"));
            final Permission permission = new Permission();
            permission.setEmail("jdoe@example.com");
            permission.setRole(Role.WRITER);
            try {
                samPermissionsImpl.setPermission(fooWorkflow, userMock, permission);
                Assert.fail("setPermissions did not throw Exception");
            } catch (CustomWebApplicationException ex) {
                // Expecting the exception, this is good
            }
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
                final List<Permission> permissions = samPermissionsImpl.setPermission(fooWorkflow, userMock, permission);
                Assert.assertEquals(permissions.size(), 1);
            } catch (CustomWebApplicationException ex) {
                Assert.fail("setPermissions did not throw Exception");
                // Expecting the exception, this is good
            }
        } catch (ApiException e) {
            Assert.fail();
            e.printStackTrace();
        }
    }

    @Test
    public void removePermissionTest() throws ApiException {
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX  + FOO_WORKFLOW_NAME))
                .thenReturn(Arrays.asList(readerAccessPolicyResponseEntry), Arrays.asList(readerAccessPolicyResponseEntry), Collections.EMPTY_LIST);
        final List<Permission> permissions = samPermissionsImpl.getPermissionsForWorkflow(userMock, fooWorkflow);
        Assert.assertEquals(permissions.size(), 1);
        try {
            samPermissionsImpl.removePermission(fooWorkflow, userMock, JANE_DOE_GMAIL_COM, Role.READER);
        } catch (CustomWebApplicationException e) {
            Assert.fail();
        }
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
