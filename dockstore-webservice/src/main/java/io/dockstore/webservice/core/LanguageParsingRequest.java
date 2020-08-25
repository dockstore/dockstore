package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("LanguageParsingRequest")
public class LanguageParsingRequest {
    @ApiModelProperty
    private String uri;
    @ApiModelProperty
    private String branch;
    @ApiModelProperty
    private String descriptorRelativePathInGit;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getDescriptorRelativePathInGit() {
        return descriptorRelativePathInGit;
    }

    public void setDescriptorRelativePathInGit(String descriptorRelativePathInGit) {
        this.descriptorRelativePathInGit = descriptorRelativePathInGit;
    }
}
