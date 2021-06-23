/*
 * Copyright 2021 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice.core.languageparsing;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

@Schema(description = "Request sent to the external language parsing service")
public class LanguageParsingRequest {

    @Schema(description = "The Git URI", required = true)
    private String uri;

    @Schema(description = "The Git branch/tag", required = true)
    private String branch;

    @Schema(description = "The relative path to the primary descriptor (relative to the base in Git)", required = true)
    private String descriptorRelativePathInGit;

    @Schema(description = "Id of the Dockstore entry", required = true)
    private long entryId;

    @Schema(description = "Id of the Dockstore entry's workflowVersion", required = true)
    private long versionId;

    @Schema(description = "List of SourceFiles that will be required for parsing hosted entries from Dockstore")
    private List<SourceFile> sourceFiles;

    @Schema(description = "The language of the workflow")
    private DescriptorLanguage descriptorLanguage;

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

    public long getEntryId() {
        return entryId;
    }

    public void setEntryId(long entryId) {
        this.entryId = entryId;
    }

    public long getVersionId() {
        return versionId;
    }

    public void setVersionId(long versionId) {
        this.versionId = versionId;
    }

    public List<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(List<SourceFile> sourceFiles) {
        this.sourceFiles = sourceFiles;
    }

    public DescriptorLanguage getDescriptorLanguage() {
        return descriptorLanguage;
    }

    public void setDescriptorLanguage(DescriptorLanguage descriptorLanguage) {
        this.descriptorLanguage = descriptorLanguage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LanguageParsingRequest that = (LanguageParsingRequest) o;
        return entryId == that.entryId && versionId == that.versionId && Objects.equals(uri, that.uri) && Objects
            .equals(branch, that.branch) && Objects.equals(descriptorRelativePathInGit, that.descriptorRelativePathInGit) && Objects
            .equals(sourceFiles, that.sourceFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, branch, descriptorRelativePathInGit, entryId, versionId, sourceFiles);
    }
}
