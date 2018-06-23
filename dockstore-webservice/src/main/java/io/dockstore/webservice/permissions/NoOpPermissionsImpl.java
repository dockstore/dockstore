package io.dockstore.webservice.permissions;

import java.util.Collections;
import java.util.List;

import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;

/**
 * A no-op <code>PermissionsInteface</code> implementation that
 * does not allow the setting of any permissions.
 */
public class NoOpPermissionsImpl implements PermissionsInterface {

    @Override
    public List<Permission> setPermission(Workflow workflow, User requester, Permission permission) {
        return Collections.emptyList();
    }

    @Override
    public List<String> workflowsSharedWithUser(User user) {
        return Collections.emptyList();
    }

    @Override
    public List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        return Collections.emptyList();
    }

    @Override
    public void removePermission(Workflow workflow, User user, String email, Role role) {

    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
        return false;
    }
}
