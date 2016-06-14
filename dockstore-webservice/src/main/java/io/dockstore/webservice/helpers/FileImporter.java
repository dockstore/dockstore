/*
 *    Copyright 2016 OICR
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

package io.dockstore.webservice.helpers;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generically imports files in order to populate Tools and Workflows.
 */
public class FileImporter {

    private final SourceCodeRepoInterface sourceCodeRepo;

    public FileImporter(SourceCodeRepoInterface sourceCodeRepo){
        this.sourceCodeRepo = sourceCodeRepo;
    }

    /**
     * Read a file from the tool's git repository.
     *
     * @param entry
     * @param fileType
     * @param version
     * @return a FileResponse instance
     */
    public String readGitRepositoryFile(Entry entry, SourceFile.FileType fileType, Version version, String specificPath) {

        if (entry.getGitUrl() == null || entry.getGitUrl().isEmpty()) {
            return null;
        }

        if (sourceCodeRepo == null) {
            return null;
        }

        final String reference = version.getReference();// sourceCodeRepo.getReference(tool.getGitUrl(), tag.getReference());

        // Do not try to get file if the reference is not available
        if (reference == null) {
            return null;
        }

        String fileName = "";
        if (specificPath != null){
            fileName = specificPath;
        } else if (version instanceof Tag) {
            Tag tag = (Tag)version;
            // Add for new descriptor types
            if (fileType == SourceFile.FileType.DOCKERFILE) {
                fileName = tag.getDockerfilePath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                fileName = tag.getCwlPath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                fileName = tag.getWdlPath();
            }
        } else if (version instanceof WorkflowVersion){
            WorkflowVersion workflowVersion = (WorkflowVersion)version;
            fileName = workflowVersion.getWorkflowPath();
        }

        return sourceCodeRepo.readFile(fileName, reference, entry.getGitUrl());
    }

    public Map<String, SourceFile> resolveImports(String content, Entry entry, SourceFile.FileType fileType, Version version) {
        Map<String, SourceFile> imports = new HashMap<>();

        YamlReader reader = new YamlReader(content);
        try {
            Map<String, ?> map = reader.read(Map.class);
            handleMap(entry, fileType, version, imports, map);

        } catch (YamlException e) {
            SourceCodeRepoInterface.LOG.error("Could not process content from "+entry.getId()+" as yaml");
        }

        Map<String, SourceFile> recursiveImports = new HashMap<>();
        for(SourceFile file : imports.values()){
            final Map<String, SourceFile> sourceFiles = resolveImports(file.getContent(), entry, fileType, version);
            recursiveImports.putAll(sourceFiles);
        }
        recursiveImports.putAll(imports);
        return recursiveImports;
    }

    private void handleMap(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports, Map<String, ?> map) {
        for(Map.Entry<String, ?> e : map.entrySet()){
            final Object mapValue = e.getValue();
            if (e.getKey().equalsIgnoreCase("$import") || e.getKey().equalsIgnoreCase("$include")){
                // handle imports and includes
                if (mapValue instanceof String) {
                    handleImport(entry, fileType, version, imports, (String) mapValue);
                }
            } else if (e.getKey().equalsIgnoreCase("run")){
                // for workflows, bare files may be referenced. See https://github.com/ga4gh/dockstore/issues/208
                if (mapValue instanceof String){
                    handleImport(entry, fileType, version, imports, (String) mapValue);
                }
            } else {
                handleMapValue(entry, fileType, version, imports, mapValue);
            }
        }
    }

    private void handleMapValue(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports, Object mapValue) {
        if(mapValue instanceof Map){
            handleMap(entry, fileType, version, imports, (Map) mapValue);
        } else if(mapValue instanceof List) {
            for(Object listMember : (List)mapValue){
                handleMapValue(entry, fileType, version, imports, listMember);
            }
        }
    }

    private void handleImport(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports, String mapValue) {
        // create a new source file
        final String fileResponse = readGitRepositoryFile(entry, fileType, version, mapValue);
        if (fileResponse == null){
            SourceCodeRepoInterface.LOG.error("Could not read: " + mapValue);
            return;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(mapValue);
        imports.put(mapValue, sourceFile);
    }
}
