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
 */package io.dockstore.webservice.permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
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

    private final Map<String, Map<String, Role>> map = new ConcurrentHashMap<>();
    private DockstoreWebserviceConfiguration configuration;

    @Override
    public List<Permission> setPermission(Workflow workflow, User requester, Permission permission) {
        Map<String, Role> entryMap = map.get(workflow.getWorkflowPath());
        if (entryMap == null) {
            entryMap = new ConcurrentHashMap<>();
            map.put(workflow.getWorkflowPath(), entryMap);
        }
        entryMap.put(permission.getEmail(), permission.getRole());
        return getPermissionsForWorkflow(requester, workflow);
    }

    @Override
    public List<String> workflowsSharedWithUser(User user) {
        return map.entrySet().stream()
                .filter(e ->  e.getValue().containsKey(user.getEmail()))
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    @Override
    public List<Permission> getPermissionsForWorkflow(User user, Workflow workflow) {
        List<Permission> owners = getHostedOwners(workflow);
        Map<String, Role> permissionMap = map.get(workflow.getWorkflowPath());
        if (permissionMap == null) {
            return owners;
        }
        List<Permission> permissionList = permissionMap.entrySet().stream().map(e -> {
            Permission permission = new Permission();
            permission.setEmail(e.getKey());
            permission.setRole(e.getValue());
            return permission;
        }).collect(Collectors.toList());
        permissionList.addAll(owners);
        return permissionList;
    }

    private List<Permission> getHostedOwners(Entry entry) {
        List<Permission> list = new ArrayList<>();
        if (entry instanceof Workflow) {
            Workflow workflow = (Workflow)entry;
            if (workflow.getMode() == WorkflowMode.HOSTED) {
                Set<User> users = entry.getUsers();
                list = users.stream().map(u -> {
                    Permission permission = new Permission();
                    permission.setRole(Role.OWNER);
                    permission.setEmail(u.getEmail() != null ? u.getEmail() : u.getUsername());
                    return permission;
                }).collect(Collectors.toList());
            }
        }
        return list;
    }

    @Override
    public void removePermission(Workflow workflow, User user, String email, Role role) {
        Map<String, Role> userPermissionMap = map.get(workflow.getWorkflowPath());
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
    public void initializePermission(Workflow workflow, User user) {
        Optional<Role> permission = getPermission(user, workflow);
        if (permission.isPresent()) {
            throw new CustomWebApplicationException("Permissions already exist", HttpStatus.SC_BAD_REQUEST);
        }
        Permission userPermission = new Permission();
        userPermission.setEmail(user.getEmail());
        userPermission.setRole(Role.OWNER);
        setPermission(workflow, user, userPermission);
    }

    @Override
    public boolean canDoAction(User user, Workflow workflow, Action action) {
        return getPermission(user, workflow).map(p -> {
            switch (p) {
            case OWNER:
                // If owner, can do anything
                return true;
            case WRITER:
                // If writer, can't delete or share
                return action != Action.DELETE && action != Action.SHARE;
            case READER:
                // If reader, can't write, delete, nor share
                return action == Action.READ;
            default:
                return false;
            }
        }).orElse(false);
    }

    private Optional<Role> getPermission(User requester, Workflow workflow) {
        final Map<String, Role> userPermissionsMap = map.get(workflow.getWorkflowPath());
        if (userPermissionsMap == null) {
            return Optional.empty();
        }
        return Optional.of(userPermissionsMap.get(requester.getEmail()));
    }

}

