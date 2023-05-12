/*
 * Copyright 2023 OICR, UCSC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.common.yaml;

import io.dockstore.common.EntryType;
import io.dockstore.common.yaml.constraints.EntryName;
import io.dockstore.common.yaml.constraints.ValidDescriptorLanguage;
import io.dockstore.common.yaml.constraints.ValidDescriptorLanguageSubclass;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * A notebook as described in a .dockstore.yml
 */
public class YamlNotebook implements Workflowish {

    private String name;
    private String format = "jupyter";
    private String language = "python";
    private String path;
    private String kernel;
    private String readMePath;
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

    /**
     * Get the format (i.e. JUPYTER) of the notebook file.
     */
    @NotNull
    @ValidDescriptorLanguage(entryType = EntryType.NOTEBOOK, message = "must be a supported notebook format (currently \"jupyter\")")
    public String getFormat() {
        return format;
    }

    /**
     * Set the format (i.e. JUPYTER) of the notebook file.
     */
    public void setFormat(String format) {
        this.format = format;
    }

    public String getSubclass() {
        return format;
    }

    /**
     * Get the programming language of the code contained in the notebook.
     */
    @NotNull
    @ValidDescriptorLanguageSubclass(entryType = EntryType.NOTEBOOK, message = "must be a supported notebook programming language (such as \"Python\")")
    public String getLanguage() {
        return language;
    }

    /**
     * Set the programming language of the code contained in the notebook.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Get the absolute path of the notebook file.
     */
    @NotNull
    public String getPath() {
        return path;
    }

    /**
     * Set the absolute path of the notebook file.
     */
    public void setPath(final String path) {
        this.path = path;
    }

    /**
     * Get the kernel image reference for the notebook.
     */
    @Size(min = 1, message = "must not be empty")
    @Pattern(regexp = "\\S++", message = "must not contain whitespace")
    public String getKernel() {
        return kernel;
    }

    /**
     * Set the kernel image reference for the notebook.
     */
    public void setKernel(final String kernel) {
        this.kernel = kernel;
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

    /**
     * Get the list of user-specified "other" files that should be
     * read and included with the notebook.
     */
    @NotNull
    public List<String> getOtherFiles() {
        return otherFiles;
    }

    /**
     * Set the list of user-specified "other" files that should be
     * read and included with the notebook.
     */
    public void setOtherFiles(List<String> otherFiles) {
        this.otherFiles = otherFiles;
    }

    public String getPrimaryDescriptorPath() {
        return getPath();
    }

    @Override
    public String getReadMePath() {
        return readMePath;
    }

    public void setReadMePath(String readMePath) {
        this.readMePath = readMePath;
    }
}
