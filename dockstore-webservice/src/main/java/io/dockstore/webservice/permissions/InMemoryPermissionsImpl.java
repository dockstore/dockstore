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
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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

    @Override
    public List<Permission> setPermission(User requester, Workflow workflow, Permission permission) {
        PermissionsInterface.checkUserNotOriginalOwner(permission.getEmail(), workflow);
        Map<String, Role> entryMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        checkIfOwner(requester, workflow, entryMap);
        if (entryMap == null) {
            entryMap = new ConcurrentHashMap<>();
            resourceToUsersAndRolesMap.put(workflow.getWorkflowPath(), entryMap);
        }
        entryMap.put(permission.getEmail(), permission.getRole());
        return getPermissionsForWorkflow(requester, workflow);
    }

    @Override
    public Map<Role, List<String>> workflowsSharedWithUser(User user) {
        final String userKey = userKey(user);
        final Map<Role, List<String>> map = new HashMap<>();
        resourceToUsersAndRolesMap.entrySet().stream().forEach(e -> {
            final Role role = e.getValue().get(userKey);
            if (role != null) {
                List<String> workflows = map.computeIfAbsent(role, k -> new ArrayList<>());
                workflows.add(e.getKey());
            }
        });
        return map;
    }

    @Override
    public List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        final List<Permission> permissions = getWorkflowPermissions(workflow);
        if (isOwner(user, permissions)) {
            return permissions;
        }
        throw new CustomWebApplicationException("Forbidden", HttpStatus.SC_FORBIDDEN);
    }

    @SuppressWarnings("checkstyle:FallThrough")
    @Override
    public List<Role.Action> getActionsForWorkflow(User user, Workflow workflow) {
        if (workflow.getUsers().contains(user)) {
            return Arrays.asList(Role.Action.values());
        }
        final ArrayList<Role.Action> actions = new ArrayList<>();
        final List<Permission> permissions = getWorkflowPermissions(workflow);
        final String userKey = userKey(user);
        final Optional<Permission> permission = permissions.stream().filter(p ->
            p.getEmail().equals(userKey)
        ).findFirst();
        if (permission.isPresent()) {
            final Role role = permission.get().getRole();
            switch (role) {
            case OWNER:
                actions.add(Role.Action.SHARE);
                actions.add(Role.Action.DELETE);
                // No break statement on purpose
            case WRITER:
                actions.add(Role.Action.WRITE);
                // No break statement on purpose
            case READER:
                actions.add(Role.Action.READ);
            default: // Checkstyle complains otherwise
            }
        }
        return actions;
    }

    private boolean isOwner(User user, List<Permission> workflowPermissions) {
        final String userKey = userKey(user);
        return workflowPermissions.stream()
                .anyMatch(p -> p.getRole() == Role.OWNER && p.getEmail().equals(userKey));
    }

    /**
     * Gets all of the permissions for the <code>workflow</code>.
     * @param workflow
     * @return
     */
    private List<Permission> getWorkflowPermissions(Workflow workflow) {
        final List<Permission> dockstoreOwners = PermissionsInterface.getOriginalOwnersForWorkflow(workflow);
        Map<String, Role> permissionMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        if (permissionMap == null) {
            return dockstoreOwners;
        }
        List<Permission> permissionList = permissionMap.entrySet().stream()
                .map(e -> new Permission(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return PermissionsInterface.mergePermissions(dockstoreOwners, permissionList);
    }

    @Override
    public void removePermission(User user, Workflow workflow, String email, Role role) {
        PermissionsInterface.checkUserNotOriginalOwner(email, workflow);
        Map<String, Role> userPermissionMap = resourceToUsersAndRolesMap.get(workflow.getWorkflowPath());
        if (userPermissionMap != null) {
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

    @Override
    public void selfDestruct(User user) {
        if (isSharing(user)) {
            throw new CustomWebApplicationException("The user is sharing at least one workflow and cannot be deleted.",
                    HttpStatus.SC_BAD_REQUEST);
        } else {
            user.getEntries().stream().forEach(e -> {
                if (e instanceof Workflow) {
                    resourceToUsersAndRolesMap.remove(((Workflow)e).getWorkflowPath());
                }
            });
        }
    }

    @Override
    public boolean isSharing(User user) {
        final String userKey = userKey(user);
        return user.getEntries().stream().anyMatch(e -> {
            if (e instanceof Workflow) {
                final Map<String, Role> map = resourceToUsersAndRolesMap.get(((Workflow)e).getWorkflowPath());
                return map != null && map.keySet().stream().anyMatch(u -> !userKey.equals(u));
            }
            return false;
        });
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
        User.Profile profile = user.getUserProfiles().get(TokenType.GOOGLE_COM.toString());
        if (profile == null || profile.email == null) {
            return user.getUsername();
        } else {
            return profile.email;
        }
    }

    private void checkIfOwner(User user, Workflow workflow, Map<String, Role> permissionMap) {
        final String userKey = userKey(user);
        if (!workflow.getUsers().contains(user)) {
            if (permissionMap == null || !permissionMap.entrySet().stream().anyMatch(e -> e.getValue() == Role.OWNER && e.getKey().equals(userKey))) {
                throw new CustomWebApplicationException("Forbidden", HttpStatus.SC_FORBIDDEN);
            }
        }
    }
}

