/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.swagger.model;

import java.util.List;

public abstract class ToolVersionPresenter extends ToolVersion {
    protected ToolVersion toolVersion;

    public String getName() {
        return this.toolVersion.getName();
    }

    public String getUrl() {
        return this.toolVersion.getUrl();
    }

    public String getId() {
        return toolVersion.getId();
    }

    public String getImage() {
        return this.toolVersion.getImage();
    }

    public List<DescriptorTypeEnum> getDescriptorType() {
        return this.toolVersion.getDescriptorType();
    }

    public Boolean getDockerfile() {
        return this.toolVersion.getDockerfile();
    }

    public String getMetaVersion() {
        return this.toolVersion.getMetaVersion();
    }

    public Boolean getVerified() {
        return this.toolVersion.getVerified();
    }

    public String getVerifiedSource() {
        return this.toolVersion.getVerifiedSource();
    }
}

