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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import org.apache.http.HttpStatus;

/**
 * Implementation of the {@link PermissionsInterface} that only reads
 * and saves settings in memory. When you bring down the
 * Java VM, all settings are lost. If more than one instance of the
 * Dockstore webservice were running, settings would NOT be replicated
 * among the instances.
 *
 * <p>This is useful for testing without having any dependencies on an external
 * authorization service.</p>
 *
 * <p>Note that this PermissionsInterface implementation does not support user groups.</p>
 */
public class InMemoryPermissionsImpl implements PermissionsInterface {

    /**
     * A map of resources to users and roles.
     *
     * The keys are resource paths, e.g., the workflow path. The values are a second map.
     *
     * The second map's keys are user keys, and the values are Roles
     *
     * The user key can either be an email or the username. With SAM/FireCloud, there must be
     * an email, since SAM only supports Google emails. This is a little bit of a hack for
     * this implementation, because we may only have the name, and not the email, of users logged in with GitHub.
     */
    private final Map<String, Map<String, Role>> resourceToUsersAndRolesMap = new ConcurrentHashMap<>();
    private DockstoreWebserviceConfiguration configuration;

    @Override
    public List<Permission> setPermission(Workflow workflow, User requester, Permission permission) {
        Map<String, Role> entryMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        if (entryMap == null) {
            entryMap = new ConcurrentHashMap<>();
            resourceToUsersAndRolesMap.put(workflow.getWorkflowPath(), entryMap);
        }
        entryMap.put(permission.getEmail(), permission.getRole());
        return getPermissionsForWorkflow(requester, workflow);
    }

    @Override
    public List<String> workflowsSharedWithUser(User user) {
        return resourceToUsersAndRolesMap.entrySet().stream()
                .filter(e ->  e.getValue().containsKey(userKey(user)))
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    @Override
    public List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        Map<String, Role> permissionMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        if (permissionMap == null) {
            return Collections.EMPTY_LIST;
        }
        List<Permission> permissionList = permissionMap.entrySet().stream().map(e -> {
            Permission permission = new Permission();
            permission.setEmail(e.getKey());
            permission.setRole(e.getValue());
            return permission;
        }).collect(Collectors.toList());
        return permissionList;
    }

    @Override
    public void removePermission(Workflow workflow, User user, String email, Role role) {
        Map<String, Role> userPermissionMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        if (userPermissionMap != null) {
            if (role == Role.OWNER) {
                // Make sure not the last owner
                if (userPermissionMap.values().stream().filter(p -> p == Role.OWNER).collect(Collectors.toList()).size() < 2) {
                    throw new CustomWebApplicationException("The last owner cannot be removed", HttpStatus.SC_BAD_REQUEST);
                }
            }
            userPermissionMap.remove(email);
        }
    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Role.Action action) {
        final Optional<Role> role = getRole(user, workflow);
        return role.map(p -> {
            switch (p) {
            case OWNER:
                // If owner, can do anything
                return true;
            case WRITER:
                // If writer, can't delete or share
                return action != Role.Action.DELETE && action != Role.Action.SHARE;
            case READER:
                // If reader, can't write, delete, nor share
                return action == Role.Action.READ;
            default:
                return false;
            }
        }).orElse(false);
    }

    private Optional<Role> getRole(User requester, Workflow workflow) {
        final Map<String, Role> userPermissionsMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        if (userPermissionsMap == null) {
            return Optional.empty();
        }
        final Role role = userPermissionsMap.get(userKey(requester));
        return Optional.of(role);
    }

    private String userKey(User user) {
        return user.getEmail() == null ? user.getUsername() : user.getEmail();
    }

}

