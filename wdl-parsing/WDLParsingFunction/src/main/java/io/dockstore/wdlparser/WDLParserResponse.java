package io.dockstore.wdlparser;

import java.util.List;

public class WDLParserResponse {
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
