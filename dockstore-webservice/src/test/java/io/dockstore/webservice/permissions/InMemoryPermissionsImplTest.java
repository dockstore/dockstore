package io.dockstore.webservice.permissions;

import static org.mockito.Mockito.when;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class InMemoryPermissionsImplTest {

    private static final String JANE_DOE_EXAMPLE_COM = "jane.doe@example.com";
    private static final String JOHN_DOE_EXAMPLE_COM = "john.doe@example.com";
    private static final String DOCKSTORE_ORG_JOHN_MYWORKFLOW = "dockstore.org/john/myworkflow";
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private InMemoryPermissionsImpl inMemoryPermissions;
    private User johnDoeUser = new User();
    private User janeDoeUser = new User();
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

        User.Profile profile = new User.Profile();
        profile.email = JOHN_DOE_EXAMPLE_COM;
        johnDoeUser.getUserProfiles().put(TokenType.GOOGLE_COM.toString(), profile);
        johnDoeUser.setUsername(JOHN_DOE_EXAMPLE_COM);
        johnDoeUser.getEntries().add(fooWorkflow);

        when(fooWorkflow.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(johnDoeUser)));
        when(gooWorkflow.getWorkflowPath()).thenReturn("goo");
        when(gooWorkflow.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(johnDoeUser)));
        when(dockstoreOrgWorkflow.getWorkflowPath()).thenReturn(DOCKSTORE_ORG_JOHN_MYWORKFLOW);
        when(dockstoreOrgWorkflow.getUsers()).thenReturn(new HashSet<>(Collections.singletonList(johnDoeUser)));

        janeDoeUser.setUsername("jane");
    }

    @Test
    public void setPermissionTest() {
        final Permission permission = new Permission(JANE_DOE_EXAMPLE_COM, Role.WRITER);
        final List<Permission> permissions = inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        Assert.assertTrue(permissions.contains(permission));
    }

    @Test
    public void workflowsSharedWithUser() {
        Assert.assertEquals(inMemoryPermissions.workflowsSharedWithUser(johnDoeUser).size(), 0);
        final Permission permission = new Permission();
        permission.setRole(Role.WRITER);
        permission.setEmail(janeDoeUser.getUsername());
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        inMemoryPermissions.setPermission(johnDoeUser, gooWorkflow, permission);
        Assert.assertEquals(1, inMemoryPermissions.workflowsSharedWithUser(janeDoeUser).size());
        permission.setRole(Role.READER);
        inMemoryPermissions.setPermission(johnDoeUser, dockstoreOrgWorkflow, permission);
        final Map<Role, List<String>> roleListMap = inMemoryPermissions.workflowsSharedWithUser(janeDoeUser);
        Assert.assertEquals(2, roleListMap.size());
        Assert.assertEquals(DOCKSTORE_ORG_JOHN_MYWORKFLOW, roleListMap.get(Role.READER).get(0));
    }

    @Test
    public void removePermission() {
        Assert.assertEquals(inMemoryPermissions.workflowsSharedWithUser(johnDoeUser).size(), 0);
        final Permission permission = new Permission("jane", Role.WRITER);
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        inMemoryPermissions.setPermission(johnDoeUser, gooWorkflow, permission);
        final Map<Role, List<String>> sharedWithUser = inMemoryPermissions.workflowsSharedWithUser(janeDoeUser);
        Assert.assertEquals(1, sharedWithUser.size());
        Assert.assertEquals(2, sharedWithUser.entrySet().iterator().next().getValue().size());
        inMemoryPermissions.removePermission(johnDoeUser, fooWorkflow, "jane", Role.WRITER);
        final Map<Role, List<String>> sharedWithUser1 = inMemoryPermissions.workflowsSharedWithUser(janeDoeUser);
        Assert.assertEquals(1, sharedWithUser1.size());
        Assert.assertEquals(1, sharedWithUser1.entrySet().iterator().next().getValue().size());
    }

    @Test
    public void canDoAction() {
        Assert.assertEquals(inMemoryPermissions.workflowsSharedWithUser(johnDoeUser).size(), 0);
        final Permission permission = new Permission(janeDoeUser.getUsername(), Role.READER);
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        Assert.assertTrue(inMemoryPermissions.canDoAction(janeDoeUser, fooWorkflow, Role.Action.READ));
        Assert.assertFalse(inMemoryPermissions.canDoAction(janeDoeUser, fooWorkflow, Role.Action.WRITE));
    }

    @Test
    public void setPermissionsUnauthorized() {
        final Permission permission = new Permission("whatever", Role.READER);
        thrown.expect(CustomWebApplicationException.class);
        inMemoryPermissions.setPermission(janeDoeUser, fooWorkflow, permission);
    }

    @Test
    public void testOwnersActions() {
        // Test that reader can see her own permission even if she is not an owner
        final Permission permission = new Permission("jane", Role.OWNER);
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        final List<Role.Action> actions = inMemoryPermissions.getActionsForWorkflow(janeDoeUser, fooWorkflow);
        Assert.assertEquals(Role.Action.values().length, actions.size()); // Owner can perform all actions
    }

    @Test
    public void testWritersActions() {
        // Test that reader can see her own permission even if she is not an owner
        final Permission permission = new Permission("jane", Role.WRITER);
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        final List<Role.Action> actions = inMemoryPermissions.getActionsForWorkflow(janeDoeUser, fooWorkflow);
        Assert.assertEquals(2, actions.size());
        Assert.assertTrue(actions.contains(Role.Action.WRITE) && actions.contains(Role.Action.WRITE));
    }

    @Test
    public void testReadersActions() {
        // Test that reader can see her own permission even if she is not an owner
        final Permission permission = new Permission("jane", Role.READER);
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        final List<Role.Action> actions = inMemoryPermissions.getActionsForWorkflow(janeDoeUser, fooWorkflow);
        Assert.assertEquals(1, actions.size());
        Assert.assertTrue(actions.contains(Role.Action.READ));
    }

    @Test
    public void testNoPermissions() {
        // Test that user without permissions querying permissions gets an exception
        thrown.expect(CustomWebApplicationException.class);
        inMemoryPermissions.getPermissionsForWorkflow(janeDoeUser, fooWorkflow);
    }

    @Test
    public void testIsSharing() {
        // Nothing shared at all
        Assert.assertFalse(inMemoryPermissions.isSharing(johnDoeUser));

        // Share with Jane
        final Permission permission = new Permission("jane", Role.OWNER);
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, permission);
        Assert.assertTrue(inMemoryPermissions.isSharing(johnDoeUser));
    }

    @Test
    public void testSelfDestruct() {
        // Nothing shared at all
        Assert.assertFalse(inMemoryPermissions.isSharing(johnDoeUser));

        inMemoryPermissions.selfDestruct(johnDoeUser);
        // Share with Jane
        inMemoryPermissions.setPermission(johnDoeUser, fooWorkflow, new Permission("jane", Role.OWNER));
        thrown.expect(CustomWebApplicationException.class);
        inMemoryPermissions.selfDestruct(johnDoeUser);
    }

}
