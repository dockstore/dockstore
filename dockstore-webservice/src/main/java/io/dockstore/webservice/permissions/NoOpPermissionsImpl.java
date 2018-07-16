package io.dockstore.webservice.permissions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;

/**
 * A no-op <code>PermissionsInteface</code> implementation that
 * does not allow the setting of any permissions.
 */
public class NoOpPermissionsImpl implements PermissionsInterface {

    @Override
    public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
        return Collections.emptyList();
    }

    @Override
    public Map<Role, List<String>> workflowsSharedWithUser(User user) {
        return Collections.emptyMap();
    }

    @Override
    public void removePermission(User user, Workflow workflow, String email, Role role) {
        PermissionsInterface.checkUserNotOriginalOwner(email, workflow);
    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
        return false;
    }
}
