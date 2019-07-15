/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.languages;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.language.MinimalLanguageInterface;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanguagePluginHandler implements LanguageHandlerInterface {

    public static final Logger LOG = LoggerFactory.getLogger(LanguagePluginHandler.class);
    private final MinimalLanguageInterface minimalLanguageInterface;

    LanguagePluginHandler(Class<? extends MinimalLanguageInterface> workflowLanguagePluginClass) {
        try {
            this.minimalLanguageInterface = workflowLanguagePluginClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            LOG.error("could not construct language plugin", e);
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Entry parseWorkflowContent(Entry entry, String filepath, String content, Set<SourceFile> sourceFiles) {
        final MinimalLanguageInterface.WorkflowMetadata workflowMetadata = minimalLanguageInterface
            .parseWorkflowForMetadata(filepath, content, new HashMap<>());
        entry.setAuthor(workflowMetadata.getAuthor());
        entry.setEmail(workflowMetadata.getEmail());
        entry.setDescription(workflowMetadata.getDescription());
        return entry;
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return new VersionTypeValidation(true, new HashMap<>());
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return new VersionTypeValidation(true, new HashMap<>());
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return new VersionTypeValidation(true, new HashMap<>());
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String filepath) {

        MinimalLanguageInterface.FileReader reader = path -> sourceCodeRepoInterface.readFile(repositoryId, path, version.getReference());

        final Map<String, Pair<String, MinimalLanguageInterface.GenericFileType>> stringPairMap = minimalLanguageInterface
            .indexWorkflowFiles(filepath, content, reader);
        Map<String, SourceFile> results = new HashMap<>();
        for (Map.Entry<String, Pair<String, MinimalLanguageInterface.GenericFileType>> entry : stringPairMap.entrySet()) {
            final SourceFile sourceFile = new SourceFile();
            sourceFile.setPath(entry.getKey());
            sourceFile.setContent(entry.getValue().getLeft());
            // DOCKSTORE-2428 - demo how to add new workflow language
            // sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_SWL);
            if (minimalLanguageInterface.isService()) {
                // TODO: this needs to be more sophisticated
                sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML);
            }
            sourceFile.setAbsolutePath(entry.getKey());
            results.put(entry.getKey(), sourceFile);
        }
        return results;
    }

    @Override
    public String getContent(String mainDescriptorPath, String mainDescriptor, Map<String, String> secondaryDescContent, Type type,
        ToolDAO dao) {
        return "";
    }
}
