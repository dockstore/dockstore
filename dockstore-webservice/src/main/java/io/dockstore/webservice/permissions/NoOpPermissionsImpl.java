package io.dockstore.webservice.permissions;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;

/**
 * A no-op <code>PermissionsInteface</code> implementation that
 * does not allow the setting of any permissions.
 */
public class NoOpPermissionsImpl implements PermissionsInterface {

    @Override
    public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
        throw new CustomWebApplicationException("Not implemented", HttpStatus.SC_NOT_IMPLEMENTED);
    }

    @Override
    public Map<Role, List<String>> workflowsSharedWithUser(User user) {
        return Collections.emptyMap();
    }

    @Override
    public List<Role.Action> getActionsForWorkflow(User user, Workflow workflow) {
        if (workflow.getUsers().contains(user)) {
            return Arrays.asList(Role.Action.values());
        }
        return Collections.emptyList();
    }

    @Override
    public void removePermission(User user, Workflow workflow, String email, Role role) {
        throw new CustomWebApplicationException("Not implemented", HttpStatus.SC_NOT_IMPLEMENTED);
    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
        return false;
    }

    @Override
    public void selfDestruct(User user) {
        // Do nothing
    }

    @Override
    public boolean isSharing(User user) {
        return false;
    }

    @Override
    public Optional<String> userIdForSharing(final User user) {
        return Optional.of(user.getUsername());
    }
}
