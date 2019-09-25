package io.dockstore.webservice.helpers;

public class WorkflowWizardEntry {
    String repository;
    Boolean exists;
    Boolean canDelete;

    public WorkflowWizardEntry(String repository, Boolean exists, Boolean canDelete) {
        this.repository = repository;
        this.exists = exists;
        this.canDelete = canDelete;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public Boolean getExists() {
        return exists;
    }

    public void setExists(Boolean exists) {
        this.exists = exists;
    }

    public Boolean getCanDelete() {
        return canDelete;
    }

    public void setCanDelete(Boolean canDelete) {
        this.canDelete = canDelete;
    }
}
