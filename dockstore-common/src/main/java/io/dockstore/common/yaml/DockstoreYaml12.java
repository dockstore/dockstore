/*
 *    Copyright 2020 OICR
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
package io.dockstore.common.yaml;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * The preferred .dockstore.yml since 1.9. Supports workflows, one-step workflows (tools), and services .
 * Workflows and tools are allowed to have multiple instances.
 *
 * Validator requires at least one service or one workflow
 */
@ValidDockstore12
public class DockstoreYaml12 implements DockstoreYaml {

    private String version;
    private List<YamlWorkflow> workflows = new ArrayList<>();
    private List<YamlWorkflow> tools = new ArrayList<>();
    private Service12 service;

    public void setVersion(final String version) {
        this.version = version;
    }

    @Valid
    @NotNull // But may be empty
    public List<YamlWorkflow> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(final List<YamlWorkflow> workflows) {
        this.workflows = workflows;
    }

    @Valid
    @NotNull // But may be empty
    public List<YamlWorkflow> getTools() {
        return tools;
    }

    public void setTools(final List<YamlWorkflow> tools) {
        this.tools = tools;
    }

    @Valid
    public Service12 getService() {
        return service;
    }

    public void setService(final Service12 service) {
        this.service = service;
    }

    @NotNull
    @Pattern(regexp = "1\\.2", message = "must be \"1.2\"")
    @Override
    public String getVersion() {
        return version;
    }

}
