package io.dockstore.webservice.permissions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class InMemoryPermissionsImplTest {

    public static final String JANE_DOE_EXAMPLE_COM = "jane.doe@example.com";
    public static final String JOHN_DOE_EXAMPLE_COM = "john.doe@example.com";
    public static final String DOCKSTORE_ORG_JOHN_MYWORKFLOW = "dockstore.org/john/myworkflow";
    private InMemoryPermissionsImpl inMemoryPermissions;
    private User userMock = Mockito.mock(User.class);
    private Workflow fooWorkflow;
    private Workflow gooWorkflow;
    private Workflow dockstoreOrgWorkflow;

    @Before
    public void setup() {
        inMemoryPermissions = new InMemoryPermissionsImpl();
        fooWorkflow = Mockito.mock(Workflow.class);
        gooWorkflow = Mockito.mock(Workflow.class);
        dockstoreOrgWorkflow = Mockito.mock(Workflow.class);
        when(fooWorkflow.getWorkflowPath()).thenReturn("foo");
        when(gooWorkflow.getWorkflowPath()).thenReturn("goo");
        when(dockstoreOrgWorkflow.getWorkflowPath()).thenReturn(DOCKSTORE_ORG_JOHN_MYWORKFLOW);
        Map<String, User.Profile> profiles = new HashMap<>();
        User.Profile profile = new User.Profile();
        profile.email = JOHN_DOE_EXAMPLE_COM;
        profiles.put(TokenType.GOOGLE_COM.toString(), profile);
        when(userMock.getUserProfiles()).thenReturn(profiles);
    }

    @Test
    public void setPermissionTest() {
        final Permission permission = new Permission();
        permission.setRole(Role.WRITER);
        permission.setEmail(JANE_DOE_EXAMPLE_COM);
        final List<Permission> permissions = inMemoryPermissions.setPermission(fooWorkflow, userMock, permission);
        Assert.assertEquals(permissions.get(0), permission);
    }

    @Test
    public void workflowsSharedWithUser() {
        Assert.assertEquals(inMemoryPermissions.workflowsSharedWithUser(userMock).size(), 0);
        final Permission permission = new Permission();
        permission.setRole(Role.WRITER);
        permission.setEmail(JOHN_DOE_EXAMPLE_COM);
        inMemoryPermissions.setPermission(fooWorkflow, userMock, permission);
        inMemoryPermissions.setPermission(gooWorkflow, userMock, permission);
        Assert.assertEquals(1, inMemoryPermissions.workflowsSharedWithUser(userMock).size());
        permission.setRole(Role.READER);
        inMemoryPermissions.setPermission(dockstoreOrgWorkflow, userMock, permission);
        final Map<Role, List<String>> roleListMap = inMemoryPermissions.workflowsSharedWithUser(userMock);
        Assert.assertEquals(2, roleListMap.size());
        Assert.assertEquals(DOCKSTORE_ORG_JOHN_MYWORKFLOW, roleListMap.get(Role.READER).get(0));
    }

    @Test
    public void removePermission() {
        Assert.assertEquals(inMemoryPermissions.workflowsSharedWithUser(userMock).size(), 0);
        final Permission permission = new Permission();
        permission.setRole(Role.WRITER);
        permission.setEmail(JOHN_DOE_EXAMPLE_COM);
        inMemoryPermissions.setPermission(fooWorkflow, userMock, permission);
        inMemoryPermissions.setPermission(gooWorkflow, userMock, permission);
        final Map<Role, List<String>> sharedWithUser = inMemoryPermissions.workflowsSharedWithUser(userMock);
        Assert.assertEquals(1, sharedWithUser.size());
        Assert.assertEquals(2, sharedWithUser.entrySet().iterator().next().getValue().size());
        inMemoryPermissions.removePermission(fooWorkflow, userMock, JOHN_DOE_EXAMPLE_COM, Role.WRITER);
        final Map<Role, List<String>> sharedWithUser1 = inMemoryPermissions.workflowsSharedWithUser(userMock);
        Assert.assertEquals(1, sharedWithUser1.size());
        Assert.assertEquals(1, sharedWithUser1.entrySet().iterator().next().getValue().size());
    }

    @Test
    public void canDoAction() {
        Assert.assertEquals(inMemoryPermissions.workflowsSharedWithUser(userMock).size(), 0);
        final Permission permission = new Permission();
        permission.setRole(Role.READER);
        permission.setEmail(JOHN_DOE_EXAMPLE_COM);
        inMemoryPermissions.setPermission(fooWorkflow, userMock, permission);
        Assert.assertTrue(inMemoryPermissions.canDoAction(userMock, fooWorkflow, Role.Action.READ));
        Assert.assertFalse(inMemoryPermissions.canDoAction(userMock, fooWorkflow, Role.Action.WRITE));
    }


}
