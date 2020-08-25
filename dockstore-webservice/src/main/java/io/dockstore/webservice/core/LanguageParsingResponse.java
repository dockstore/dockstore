package io.dockstore.webservice.core;

import java.util.List;

import io.swagger.annotations.ApiModel;

@ApiModel("LanguageParsingResponse")
public class LanguageParsingResponse {
    private String clonedRepositoryAbsolutePath;
    private boolean isValid;
    private List<String> secondaryFilePaths;

    public List<String> getSecondaryFilePaths() {
        return secondaryFilePaths;
    }

    public void setSecondaryFilePaths(List<String> secondaryFilePaths) {
        this.secondaryFilePaths = secondaryFilePaths;
    }

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
}
