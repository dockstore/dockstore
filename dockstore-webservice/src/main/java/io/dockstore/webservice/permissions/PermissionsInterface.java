/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.permissions;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Profile;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;

/**
 * <p>
 * Abstracts out the backend permissions service.
 * </p>
 * <p>
 * A <code>Permission</code> is an email address
 * and a  {@link Role},
 * an enum whose values are <code>OWNER</code>,
 * <code>WRITER</code>, and <code>READER</code>.
 * </p>
 * <p>
 * An email address can be for a single user or for a group.
 * </p>
 * <p>
 * An <code>Workflow</code> can have multiple <code>Permission</code>s associated
 * with it.
 * </p>
 *
 * <p>
 *     If we extend sharing to tools, should we create additional methods for tools here,
 *     e.g., <code>setPermission(Tool tool, User requester, Permission permission)</code>,
 *     or should we instead make this interface more generic, and just require a
 *     GA4GH-style path, e.g., <code>setPermission(String path, User requester, Permission permission)</code>,
 *     where for setting a workflow's permission one would invoke <code>setPermission("#workflow/myworkflow", ...)</code>
 *     and for setting a tool's permission one would invoke <code>setPermission("mytool", ...)</code>?
 * </p>
 */
public interface PermissionsInterface {

    /**
     * Adds or modifies a <code>Permission</code> to have the specified permissions on an workflow.
     *
     * <p>If the email in <code>permission</code> does not have any permissions on the workflow,
     * the email is added with the specified permission. If the email already has a permission,
     * then the permission is updated.</p>
     * @param requester -- the requester, who must be an owner of <code>workflow</code> or an admin
     * @param workflow the workflow
     * @param permission -- the email and the permission for that email
     * @return the list of the workflow's permissions after having added or modified
     */
    List<Permission> setPermission(User requester, Workflow workflow, Permission permission);

    /**
     * Returns a map of all the paths of all workflows that have been shared with the specified <code>user</code>.
     *
     * <p>Each key in the map is a {@link Role}, and the value is a list of workflow
     * paths of workflows for which the user has that role. The paths are NOT encoded.</p>
     *
     * @param user
     * @return this list of all entries shared with the user
     */
    Map<Role, List<String>> workflowsSharedWithUser(User user);

    /**
     * Lists all <code>Permission</code>s for <code>workflow</code>
     * @param user the user, who must either be an owner of the workflow or an admin
     * @param workflow the workflow
     * @return a list of users and their permissions
     */
    default List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        if (!workflow.getUsers().contains(user)) {
            throw new CustomWebApplicationException("Forbidden", HttpStatus.SC_FORBIDDEN);
        }
        return PermissionsInterface.getOriginalOwnersForWorkflow(workflow);
    }

    /**
     * List all {@link Role.Action} <code>user</code> can perform on <code>workflow</code>.
     * @param user
     * @param workflow
     * @return a list of allowed actions on a workflow, possibly empty
     */
    List<Role.Action> getActionsForWorkflow(User user, Workflow workflow);

    /**
     * Removes the <code>email</code> from the <code>role</code> from
     * <code>workflow</code>'s permissions.
     * @param user the requester, must be an owner of <code>workflow</code> or an admin.
     * @param workflow
     * @param email the email of the user to remove
     * @param role
     */
    void removePermission(User user, Workflow workflow, String email, Role role);

    /**
     * Indicates whether the <code>user</code> can perform the given <code>action</code> on the
     * specified <code>workflow</code>.
     *
     * @param user
     * @param workflow
     * @param action
     * @return
     */
    boolean canDoAction(User user, Workflow workflow, Role.Action action);

    /**
     * Deletes all sharing artifacts that the user is an owner of. This method will fail with a {@link CustomWebApplicationException}
     * if the user is sharing anything.
     * @param user
     */
    void selfDestruct(User user);

    /**
     * Indicates whether a user is sharing any workflows.
     *
     * @param user
     * @return
     */
    boolean isSharing(User user);

    /**
     * Merges two lists of permissions, removing any duplicate users. If there
     * are duplicates, gives precedence to <code>dockstoreOwners</code>.
     *
     * @param dockstoreOwners
     * @param nativePermissions
     * @return
     */
    static List<Permission> mergePermissions(List<Permission> dockstoreOwners, List<Permission> nativePermissions) {
        final ArrayList<Permission> permissions = new ArrayList<>(dockstoreOwners);
        final Set<String> dockstoreOwnerEmails = permissions.stream().map(p -> p.getEmail()).collect(Collectors.toSet());
        permissions.addAll(nativePermissions.stream()
                .filter(p -> !dockstoreOwnerEmails.contains(p.getEmail())).collect(Collectors.toList()));
        return permissions;
    }

    /**
     * Returns the "original owners" of a workflow as list of {@link Permission}. The original owners
     * are the <code>workflow.getUsers()</code>, e.g., who created the hosted entry, or members of the
     * GitHub repo.
     *
     * @param workflow
     * @return
     */
    static List<Permission> getOriginalOwnersForWorkflow(Workflow workflow) {
        return workflow.getUsers().stream()
                .map(user -> {
                    // This is ugly in order to support both SAM and InMemory authorizers
                    final Profile profile = user.getUserProfiles().get(TokenType.GOOGLE_COM.toString());
                    if (profile != null && profile.email != null) {
                        return profile.email;
                    } else {
                        return user.getUsername();
                    }
                })
                .filter(email -> email != null)
                .map(email -> {
                    final Permission permission = new Permission();
                    permission.setEmail(email);
                    permission.setRole(Role.OWNER);
                    return permission;
                })
                .collect(Collectors.toList());
    }

    /**
     * Checks if a the username is an "original owner" of a workflow, and throws a
     * {@link CustomWebApplicationException} if it is. To be used as a check so
     * that original owners can never be removed as owners.
     *
     * This method compares <code>username</code> against <code>User.getUsername()</code> of the <code>workflow</code>'s users, only.
     * That username property is currently set with either the Github username or the Google user email, depending
     * on the order in which the user linked accounts.
     *
     * @param username
     * @param workflow
     */
    static void checkUserNotOriginalOwner(String username, Workflow workflow) {
        if (workflow.getUsers().stream().anyMatch(u ->  username.equals(u.getUsername()))) {
            throw new CustomWebApplicationException(username + " is an original owner and their permissions cannot be modified",
                    HttpStatus.SC_FORBIDDEN);
        }
    }
}
