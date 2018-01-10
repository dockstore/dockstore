package io.swagger.model;

import java.util.List;

/**
 * @author gluu
 * @since 21/12/17
 */
public abstract class ToolPresenter extends Tool {
    Tool tool;

    public String getUrl() {
        return this.tool.getUrl();
    }

    public String getId() {
        return this.tool.getId();
    }

    public String getOrganization() {
        return this.tool.getOrganization();
    }

    public String getToolname() {
        return this.tool.getToolname();
    }

    public ToolClass getToolclass() {
        return this.tool.getToolclass();
    }

    public String getDescription() {
        return this.tool.getDescription();
    }

    public String getAuthor() {
        return this.tool.getAuthor();
    }

    public String getMetaVersion() {
        return tool.getMetaVersion();
    }

    public List<String> getContains() {
        return this.tool.getContains();
    }

    public Boolean getVerified() {
        return this.tool.getVerified();
    }

    public String getVerifiedSource() {
        return tool.getVerifiedSource();
    }

    public Boolean getSigned() {
        return this.tool.getSigned();
    }

    public List<ToolVersion> getVersions() {
        return this.tool.getVersions();
    }
}
