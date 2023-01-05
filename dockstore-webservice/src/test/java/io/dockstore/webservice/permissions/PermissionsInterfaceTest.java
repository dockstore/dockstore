package io.dockstore.webservice.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PermissionsInterfaceTest {

    private static final String JOHN_DOE_EXAMPLE_COM = "john.doe@example.com";
    private static final String JANE_DOE_EXAMPLE_COM = "jane.doe@example.com";

    private User userJohn;
    private Permission janeDoeOwnerPermission;
    private Permission janeDoeWriterPermission;
    private User userJane;
    private PermissionsInterface permissionsInterface;
    private Workflow mockedWorkflow = Mockito.mock(Workflow.class);

    @BeforeEach
    public void setup() {
        userJohn = new User();
        userJohn.setUsername(JOHN_DOE_EXAMPLE_COM);
        final User.Profile profile1 = new User.Profile();
        profile1.email = JOHN_DOE_EXAMPLE_COM;
        userJohn.getUserProfiles().put(TokenType.GOOGLE_COM.toString(), profile1);

        userJane = new User();
        userJane.setUsername(JANE_DOE_EXAMPLE_COM);
        final User.Profile profile2 = new User.Profile();
        profile2.email = JANE_DOE_EXAMPLE_COM;
        userJane.getUserProfiles().put(TokenType.GOOGLE_COM.toString(), profile2);

        janeDoeOwnerPermission = new Permission();
        janeDoeOwnerPermission.setRole(Role.OWNER);
        janeDoeOwnerPermission.setEmail(JANE_DOE_EXAMPLE_COM);
        janeDoeWriterPermission = new Permission();
        janeDoeWriterPermission.setEmail(JOHN_DOE_EXAMPLE_COM);
        janeDoeWriterPermission.setRole(Role.WRITER);

        permissionsInterface = new PermissionsInterface() {
            @Override
            public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
                return null;
            }

            @Override
            public Map<Role, List<String>> workflowsSharedWithUser(User user) {
                return null;
            }

            @Override
            public List<Role.Action> getActionsForWorkflow(User user, Workflow workflow) {
                return Collections.emptyList();
            }

            @Override
            public void removePermission(User user, Workflow workflow, String email, Role role) {
            }

            @Override
            public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
                return false;
            }

            @Override
            public void selfDestruct(User user) {

            }

            @Override
            public boolean isSharing(User user) {
                return false;
            }

            @Override
            public Optional<String> userIdForSharing(final User user) {
                return Optional.of(user.getUsername());
            }
        };
    }

    @Test
    public void mergePermissions() {
        assertEquals(2, PermissionsInterface.mergePermissions(Collections.singletonList(janeDoeOwnerPermission), Arrays.asList(
                janeDoeOwnerPermission, janeDoeWriterPermission)).size());
    }

    @Test
    public void getOriginalOwnersForWorkflow() {
        final Set<User> users = new HashSet<>(Collections.singletonList(userJohn));
        mockedWorkflow = Mockito.mock(Workflow.class);
        when(mockedWorkflow.getUsers()).thenReturn(users);

        assertEquals(1, permissionsInterface.getOriginalOwnersForWorkflow(mockedWorkflow).size());
    }

    @Test
    public void unauthorizedGetPermissionsForWorkflow() {
        final Set<User> users = new HashSet<>(Collections.singletonList(userJohn));
        when(mockedWorkflow.getUsers()).thenReturn(users);
        assertThrows(CustomWebApplicationException.class, () -> permissionsInterface.getPermissionsForWorkflow(userJane, mockedWorkflow));
    }

    @Test
    public void checkUserNotOriginalOwner() {
        final Set<User> users = new HashSet<>(Collections.singletonList(userJohn));
        when(mockedWorkflow.getUsers()).thenReturn(users);
        assertThrows(CustomWebApplicationException.class, () -> {
            // this one was originally weird with the error on the first line
            PermissionsInterface.checkUserNotOriginalOwner(JOHN_DOE_EXAMPLE_COM, mockedWorkflow);
            PermissionsInterface.checkUserNotOriginalOwner(JANE_DOE_EXAMPLE_COM, mockedWorkflow);
        });
    }
}
