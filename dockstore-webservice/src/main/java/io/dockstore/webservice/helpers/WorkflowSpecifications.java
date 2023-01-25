package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;

public class WorkflowSpecifications {

    private List<String> userFilePaths = List.of();

    public WorkflowSpecifications() {
    }

    public void setUserFilePaths(List<String> userFilePaths) {
        this.userFilePaths = new ArrayList<>(userFilePaths);
    }

    public List<String> getUserFilePaths() {
        return userFilePaths;
    }
}
