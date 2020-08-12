package io.dockstore.wdlparser;

import java.util.List;

public class Response {
    public List<String> getSecondaryFilePaths() {
        return secondaryFilePaths;
    }

    public void setSecondaryFilePaths(List<String> secondaryFilePaths) {
        this.secondaryFilePaths = secondaryFilePaths;
    }

    private List<String> secondaryFilePaths;
    private boolean isValid;

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public String getClonedRepositoryAbsolutePath() {
        return clonedRepositoryAbsolutePath;
    }

    public void setClonedRepositoryAbsolutePath(String clonedRepositoryAbsolutePath) {
        this.clonedRepositoryAbsolutePath = clonedRepositoryAbsolutePath;
    }

    private String clonedRepositoryAbsolutePath;
}
