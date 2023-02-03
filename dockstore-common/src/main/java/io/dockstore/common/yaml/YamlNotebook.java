// TODO insert copyright

package io.dockstore.common.yaml;

import io.dockstore.common.yaml.constraints.EntryName;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * A notebook as described in a .dockstore.yml
 */
public class YamlNotebook implements Workflowish {

    private String name;
    private String format = "ipynb";
    private String language = "python";
    private String path;
    private Boolean publish;
    private boolean latestTagAsDefault = false;
    private Filters filters = new Filters();
    private List<YamlAuthor> authors = new ArrayList<>();
    private List<String> testParameterFiles = new ArrayList<>();
    private List<String> otherFiles = new ArrayList<>();

    @EntryName
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @NotNull
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSubclass() {
        return format;
    }

    @NotNull
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @NotNull
    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public Boolean getPublish() {
        return publish;
    }

    public void setPublish(final Boolean publish) {
        this.publish = publish;
    }

    public boolean getLatestTagAsDefault() {
        return latestTagAsDefault;
    }

    public void setLatestTagAsDefault(boolean latestTagAsDefault) {
        this.latestTagAsDefault = latestTagAsDefault;
    }

    @Valid
    public Filters getFilters() {
        return filters;
    }

    public void setFilters(final Filters filters) {
        this.filters = filters;
    }

    @Valid
    public List<YamlAuthor> getAuthors() {
        return authors;
    }

    public void setAuthors(final List<YamlAuthor> authors) {
        this.authors = authors;
    }

    public List<String> getTestParameterFiles() {
        return testParameterFiles;
    }

    public void setTestParameterFiles(final List<String> testParameterFiles) {
        this.testParameterFiles = testParameterFiles;
    }

    @NotNull
    public List<String> getOtherFiles() {
        return otherFiles;
    }

    public void setOtherFiles(List<String> otherFiles) {
        this.otherFiles = otherFiles;
    }

    public String getPrimaryDescriptorPath() {
        return getPath();
    }
}
