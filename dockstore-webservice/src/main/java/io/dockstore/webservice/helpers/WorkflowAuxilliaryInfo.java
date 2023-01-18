package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;

public class WorkflowAuxilliaryInfo {

    private List<String> otherFilePaths = List.of();

    public WorkflowAuxilliaryInfo() {
    }

    public void setOtherFilePaths(List<String> otherFilePaths) {
        this.otherFilePaths = new ArrayList<>(otherFilePaths);
    }

    public List<String> getOtherFilePaths() {
        return otherFilePaths;
    }
}
