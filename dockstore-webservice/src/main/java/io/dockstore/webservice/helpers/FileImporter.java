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
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;

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
     * @param tool
     * @param fileType
     * @param tag
     * @return a FileResponse instance
     */
    public String readGitRepositoryFile(Tool tool, SourceFile.FileType fileType, Tag tag, String specificPath) {

        if (tool.getGitUrl() == null || tool.getGitUrl().isEmpty()) {
            return null;
        }

        if (sourceCodeRepo == null) {
            return null;
        }

        final String reference = tag.getReference();// sourceCodeRepo.getReference(tool.getGitUrl(), tag.getReference());

        // Do not try to get file if the reference is not available
        if (reference == null) {
            return null;
        }

        String fileName = "";
        if (specificPath != null){
            fileName = specificPath;
        } else {
            // Add for new descriptor types
            if (fileType == SourceFile.FileType.DOCKERFILE) {
                fileName = tag.getDockerfilePath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                fileName = tag.getCwlPath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                fileName = tag.getWdlPath();
            }
        }

        return sourceCodeRepo.readFile(fileName, reference, tool.getGitUrl());
    }

    public Map<String, SourceFile> resolveImports(String content, Tool tool, SourceFile.FileType fileType, Tag tag) {
        Map<String, SourceFile> imports = new HashMap<>();

        YamlReader reader = new YamlReader(content);
        try {
            Map<String, ?> map = reader.read(Map.class);
            handleMap(tool, fileType, tag, imports, map);

        } catch (YamlException e) {
            SourceCodeRepoInterface.LOG.error("Could not process content from "+tool.getToolPath()+" as yaml");
        }

        Map<String, SourceFile> recursiveImports = new HashMap<>();
        for(SourceFile file : imports.values()){
            final Map<String, SourceFile> sourceFiles = resolveImports(file.getContent(), tool, fileType, tag);
            recursiveImports.putAll(sourceFiles);
        }
        recursiveImports.putAll(imports);
        return recursiveImports;
    }

    private void handleMap(Tool tool, SourceFile.FileType fileType, Tag tag, Map<String, SourceFile> imports, Map<String, ?> map) {
        for(Map.Entry<String, ?> e : map.entrySet()){
            final Object mapValue = e.getValue();
            if (e.getKey().equalsIgnoreCase("$import")){
                if (mapValue instanceof String) {
                    handleImport(tool, fileType, tag, imports, (String) mapValue);
                }
            } else {
                handleMapValue(tool, fileType, tag, imports, mapValue);
            }
        }
    }

    private void handleMapValue(Tool tool, SourceFile.FileType fileType, Tag tag, Map<String, SourceFile> imports, Object mapValue) {
        if(mapValue instanceof Map){
            handleMap(tool, fileType, tag, imports, (Map) mapValue);
        } else if(mapValue instanceof List) {
            for(Object listMember : (List)mapValue){
                handleMapValue(tool, fileType, tag, imports, listMember);
            }
        }
    }

    private void handleImport(Tool tool, SourceFile.FileType fileType, Tag tag, Map<String, SourceFile> imports, String mapValue) {
        // create a new source file
        final String fileResponse = readGitRepositoryFile(tool, fileType, tag, mapValue);
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
