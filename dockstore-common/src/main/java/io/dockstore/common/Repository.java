package io.dockstore.common;

/**
 * Class used by registration wizard to for toggle view
 * @author aduncan
 * @since 1.8.0
 */
public class Repository {
    String organization;
    String repository;
    SourceControl gitRegistry;
    boolean isPresent;

    public Repository(String organization, String repository, SourceControl gitRegistry, boolean isPresent) {
        this.organization = organization;
        this.repository = repository;
        this.gitRegistry = gitRegistry;
        this.isPresent = isPresent;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public SourceControl getGitRegistry() {
        return gitRegistry;
    }

    public void setGitRegistry(SourceControl gitRegistry) {
        this.gitRegistry = gitRegistry;
    }

    public boolean isPresent() {
        return isPresent;
    }

    public void setPresent(boolean present) {
        isPresent = present;
    }

    public String getPath() {
        return organization + "/" + repository;
    }
}
