// TODO add copyright header

package io.dockstore.common.yaml;

import io.dockstore.common.yaml.constraints.HasEntry;
import io.dockstore.common.yaml.constraints.NamesAreUnique;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * The preferred .dockstore.yml since 1.14. Supports notebooks, workflows, one-step workflows (tools), and services.
 * Notebooks, workflows, and tools are allowed to have multiple instances.
 */
@HasEntry
@NamesAreUnique
public class DockstoreYaml13 implements DockstoreYaml12AndUp {

    private String version;
    private List<YamlNotebook> notebooks = new ArrayList<>();
    private List<YamlWorkflow> workflows = new ArrayList<>();
    private List<YamlTool> tools = new ArrayList<>();
    private Service12 service;

    public void setVersion(final String version) {
        this.version = version;
    }

    @Valid
    @NotNull
    public List<YamlNotebook> getNotebooks() {
        return notebooks;
    }

    public void setNotebooks(final List<YamlNotebook> notebooks) {
        this.notebooks = notebooks;
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
    public List<YamlTool> getTools() {
        return tools;
    }

    public void setTools(final List<YamlTool> tools) {
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

    @Override
    public List<Workflowish> getEntries() {
        List<Workflowish> entries = new ArrayList<>();
        Optional.ofNullable(workflows).ifPresent(entries::addAll);
        Optional.ofNullable(notebooks).ifPresent(entries::addAll);
        Optional.ofNullable(tools).ifPresent(entries::addAll);
        Optional.ofNullable(service).ifPresent(entries::add);
        return entries;
    }
 
    @Override
    public List<String> getEntryTerms() {
        return List.of("workflow", "notebook", "tool", "service");
    }
}
