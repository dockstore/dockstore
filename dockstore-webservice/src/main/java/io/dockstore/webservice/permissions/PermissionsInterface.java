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

import java.util.List;

import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;

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
     * @param workflow the workflow
     * @param requester -- the requester, who must be an owner of <code>workflow</code> or an admin
     * @param permission -- the email and the permission for that email
     * @return the list of the workflow's permissions after having added or modified
     */
    List<Permission> setPermission(Workflow workflow, User requester, Permission permission);

    /**
     * Returns the workflow paths of all entries that have been shared with the specified <code>user</code>.
     *
     * @param user
     * @return this list of all entries shared with the user
     */
    List<String> workflowsSharedWithUser(User user);

    /**
     * Lists all <code>Permission</code>s for <code>workflow</code>
     * @param user the user, who must either be an owner of the workflow or an admin
     * @param workflow the workflow
     * @return a list of users and their permissions
     */
    List<Permission> getPermissionsForWorkflow(User user, Workflow workflow);

    /**
     * Removes the <code>email</code> from the <code>role</code> from
     * <code>workflow</code>'s permissions.
     * @param workflow
     * @param user the requester, must be an owner of <code>workflow</code> or an admin.
     * @param email the email of the user to remove
     * @param role
     */
    void removePermission(Workflow workflow, User user, String email, Role role);

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
}
