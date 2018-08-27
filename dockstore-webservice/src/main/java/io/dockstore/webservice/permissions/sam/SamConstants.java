package io.dockstore.webservice.permissions.sam;

import io.dockstore.webservice.permissions.Role;

/**
 * Constant for values that exist in SAM. These values are
 * used when making SAM API calls.
 */
public interface SamConstants {
    // SAM policy names
    String READ_POLICY = "reader";
    String WRITE_POLICY = "writer";
    String OWNER_POLICY = "owner";


    enum SamActions {
        DELETE("delete"),
        READ_POLICIES("read_policies"),
        ALTER_POLICIES("alter_policies"),
        WRITE("write"),
        READ("read");

        private final String name;

        SamActions(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * The resource type under which all Dockstore permissions are stored.
     */
    String RESOURCE_TYPE = "dockstore-tool";

    /**
     * The prefix for resource names for workflows. Permissions calls for tools
     * should omit the prefix.
     */

    String WORKFLOW_PREFIX = "#workflow/";
    String ENCODED_WORKFLOW_PREFIX = "%23workflow%2F";

    static String toSamAction(Role.Action action) {
        switch (action) {
        case WRITE:
            return SamActions.WRITE.toString();
        case READ:
            return SamActions.READ.toString();
        case DELETE:
            return SamActions.DELETE.toString();
        case SHARE:
            return SamActions.ALTER_POLICIES.toString();
        default:
            throw new IllegalArgumentException();
        }
    }
}
