package io.dockstore.webservice.permissions;

import java.util.List;

import io.dockstore.webservice.core.Workflow;

/**
 * The representation of a role and all workflows
 * that have that role.
 */
public class SharedWorkflows {
    private final Role role;
    private final List<Workflow> workflows;

    public SharedWorkflows(Role role, List<Workflow> workflows) {
        this.role = role;
        this.workflows = workflows;
    }

    public Role getRole() {
        return role;
    }

    public List<Workflow> getWorkflows() {
        return workflows;
    }

}
