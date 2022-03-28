package io.dockstore.webservice.permissions;

import io.dockstore.webservice.core.Workflow;
import java.util.List;

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
