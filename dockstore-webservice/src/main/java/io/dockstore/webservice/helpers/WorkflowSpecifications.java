package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains information used to create/update a Workflow/WorkflowVersion
 * that is not necessarily part of those objects themselves.  An instance
 * of this class is intended as an ephemeral vessel used to pass
 * information between the code that parses user input (.dockstore.yml)
 * and the workflow/version creation code.
 *
 * This class relates to workflows/versions as a blueprint does to a
 * building, or a recipe to dinner: it contains information used to create
 * the object, but it itself is not part of the object.
 */
public class WorkflowSpecifications {

    private List<String> userFilePaths = List.of();

    public WorkflowSpecifications() {
    }

    /**
     * Set the list of user-specified file paths.
     */
    public void setUserFilePaths(List<String> userFilePaths) {
        this.userFilePaths = new ArrayList<>(userFilePaths);
    }

    /**
     * Get the list of user-specified file paths.
     */
    public List<String> getUserFilePaths() {
        return userFilePaths;
    }
}
