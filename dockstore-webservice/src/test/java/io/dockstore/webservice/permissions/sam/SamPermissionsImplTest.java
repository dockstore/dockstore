package io.dockstore.webservice.permissions.sam;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class SamPermissionsImplTest {

    public static final String FOO_WORKFLOW_NAME = "foo";
    public static final String GOO_WORKFLOW_NAME = "goo";
    private AccessPolicyResponseEntry ownerPolicy;
    private AccessPolicyResponseEntry writerPolicy;
    private SamPermissionsImpl samPermissionsImpl;
    private User userMock = Mockito.mock(User.class);
    private ResourcesApi resourcesApiMock;
    private ApiClient apiClient;
    private Workflow fooWorkflow;

    @Before
    public void setup() {
        ownerPolicy = new AccessPolicyResponseEntry();
        ownerPolicy.setPolicyName("owner");
        AccessPolicyMembership accessPolicyMembership = new AccessPolicyMembership();
        accessPolicyMembership.setRoles(Arrays.asList(new String[] {"owner"}));
        accessPolicyMembership.setMemberEmails(Arrays.asList(new String [] {"jdoe@ucsc.edu"}));
        ownerPolicy.setPolicy(accessPolicyMembership);

        writerPolicy = new AccessPolicyResponseEntry();
        writerPolicy.setPolicyName("writer");
        AccessPolicyMembership writerMembership = new AccessPolicyMembership();
        writerMembership.setRoles(Arrays.asList(new String[] {"writer"}));
        writerMembership.setMemberEmails(Arrays.asList(new String[] {"jane.doe@gmail.com"}));
        writerPolicy.setPolicy(writerMembership);

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
    }

    @Test
    public void testAccessPolicyResponseEntryToUserPermissions() {
        Permission ownerPermission = new Permission();
        ownerPermission.setEmail("jdoe@ucsc.edu");
        ownerPermission.setRole(Role.OWNER);
        Assert.assertThat(samPermissionsImpl
                .accessPolicyResponseEntryToUserPermissions(Arrays.asList(ownerPolicy)),
                CoreMatchers.is(Arrays.asList(ownerPermission)));

        Permission writerPermission = new Permission();
        writerPermission.setEmail("jane.doe@gmail.com");
        writerPermission.setRole(Role.WRITER);

        Assert.assertThat(samPermissionsImpl
            .accessPolicyResponseEntryToUserPermissions(Arrays.asList(ownerPolicy, writerPolicy)),
            CoreMatchers.is(Arrays.asList(new Permission[] {ownerPermission, writerPermission})));
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
        reader.setResourceId(FOO_WORKFLOW_NAME);
        reader.setAccessPolicyName(SamConstants.READ_POLICY);
        ResourceAndAccessPolicy owner = new ResourceAndAccessPolicy();
        owner.setResourceId(GOO_WORKFLOW_NAME);
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
        AccessPolicyMembership accessPolicyMembership = new AccessPolicyMembership();
        AccessPolicyResponseEntry accessPolicyResponseEntry = new AccessPolicyResponseEntry();
        accessPolicyResponseEntry.setPolicy(accessPolicyMembership);
        accessPolicyResponseEntry.setPolicyName(SamConstants.READ_POLICY);
        accessPolicyResponseEntry.getPolicy().addRolesItem(SamConstants.READ_POLICY.toString());
        final String janeEmail = "jane@example.org";
        accessPolicyResponseEntry.getPolicy().addMemberEmailsItem(janeEmail);
        when(resourcesApiMock.listResourcePolicies(SamConstants.RESOURCE_TYPE, SamConstants.WORKFLOW_PREFIX + FOO_WORKFLOW_NAME))
                .thenReturn(Collections.emptyList(), Arrays.asList(accessPolicyResponseEntry));
        Permission permission = new Permission();
        permission.setEmail(janeEmail);
        permission.setRole(Role.READER);
        List<Permission> permissions = samPermissionsImpl.setPermission(fooWorkflow, userMock, permission);
        Assert.assertEquals(permissions.size(), 1);
    }

}
