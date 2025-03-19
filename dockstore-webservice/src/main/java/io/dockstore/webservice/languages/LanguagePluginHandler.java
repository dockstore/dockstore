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

import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.language.CompleteLanguageInterface;
import io.dockstore.language.MinimalLanguageInterface;
import io.dockstore.language.MinimalLanguageInterface.FileMetadata;
import io.dockstore.language.MinimalLanguageInterface.FileReader;
import io.dockstore.language.MinimalLanguageInterface.GenericFileType;
import io.dockstore.language.RecommendedLanguageInterface;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanguagePluginHandler implements LanguageHandlerInterface {

    public static final Logger LOG = LoggerFactory.getLogger(LanguagePluginHandler.class);
    private final MinimalLanguageInterface minimalLanguageInterface;
    private final Gson gson = new Gson();

    LanguagePluginHandler(Class<? extends MinimalLanguageInterface> workflowLanguagePluginClass) {
        try {
            this.minimalLanguageInterface = workflowLanguagePluginClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            LOG.error("could not construct language plugin", e);
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Version parseWorkflowContent(String filepath, String content, Set<SourceFile> sourceFiles, Version version) {
        final MinimalLanguageInterface.WorkflowMetadata workflowMetadata =
            minimalLanguageInterface.parseWorkflowForMetadata(filepath, content, new HashMap<>());
        // Add authors from descriptor if there are no .dockstore.yml authors
        if (workflowMetadata.getAuthor() != null && version.getAuthors().isEmpty()) {
            Author author = new Author(workflowMetadata.getAuthor());
            author.setEmail(workflowMetadata.getEmail());
            version.addAuthor(author);
        }

        // TODO: this can probably be removed after EntryResource.updateLanguageVersions is removed since this should only happen with previously imported data
        // with old data, we need to re-parse original content since original parsing lacked file-level metadata
        final Map<String, FileMetadata> stringFileMetadataMap = minimalLanguageInterface.indexWorkflowFiles(filepath, content, new FileReader() {
            @Override
            public String readFile(String path) {
                return sourceFiles.stream().filter(file -> file.getPath().equals(path)).findFirst().map(SourceFile::getContent).orElse(null);
            }

            @Override
            public List<String> listFiles(String pathToDirectory) {
                return sourceFiles.stream().map(SourceFile::getPath).toList();
            }
        });
        // save file versioning back to source files
        sourceFiles.forEach(file -> {
            final FileMetadata fileMetadata = stringFileMetadataMap.get(file.getPath());
            if (fileMetadata != null) {
                file.getMetadata().setTypeVersion(fileMetadata.languageVersion());
            }
        });
        version.setDescriptorTypeVersionsFromSourceFiles(sourceFiles);

        version.setDescriptionAndDescriptionSource(workflowMetadata.getDescription(), DescriptionSource.DESCRIPTOR);
        // TODO: hook up validation object to version for parsing metadata
        return version;
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath, Workflow workflow) {
        if (minimalLanguageInterface instanceof RecommendedLanguageInterface) {
            Optional<SourceFile> mainDescriptor = sourcefiles.stream()
                    .filter((sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath))).findFirst();
            String content = null;
            if (mainDescriptor.isPresent()) {
                content = mainDescriptor.get().getContent();
            } else {
                Map<String, String> validationMessage = new HashMap<>();
                validationMessage.put("Unknown", "Primary descriptor file not found.");
                return new VersionTypeValidation(false, validationMessage);
            }

            try {
                return ((RecommendedLanguageInterface)minimalLanguageInterface)
                    .validateWorkflowSet(primaryDescriptorFilePath, content, sourcefilesToIndexedFiles(sourcefiles));
            } catch (Exception e) {
                throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_UNPROCESSABLE_ENTITY);
            }
        } else {
            return new VersionTypeValidation(true, Collections.emptyMap());
        }
    }

    /**
     * Converts a set of sourcesfiles into the generic indexed files
     * @param sourceFiles set of sourcefiles
     * @return Generic indexed files mapping
     */
    private Map<String, FileMetadata> sourcefilesToIndexedFiles(Set<SourceFile> sourceFiles) {
        Map<String, FileMetadata> indexedFiles = new HashMap<>();

        for (SourceFile file : sourceFiles) {
            String content = file.getContent();
            String absolutePath = file.getAbsolutePath();

            FileType fileType = file.getType();
            if (fileType == null) {
                LOG.error("File type for source file {} is null", file.getPath());
                throw new CustomWebApplicationException("File type for source file "
                    + file.getPath() + " is null", HttpStatus.SC_METHOD_FAILURE);
            }
            MinimalLanguageInterface.GenericFileType genericFileType;
            switch (fileType.getCategory()) {
            case GENERIC_DESCRIPTOR:
            case PRIMARY_DESCRIPTOR:
            case SECONDARY_DESCRIPTOR:
            case OTHER:
                genericFileType = MinimalLanguageInterface.GenericFileType.IMPORTED_DESCRIPTOR;
                break;
            case TEST_FILE:
                genericFileType = MinimalLanguageInterface.GenericFileType.TEST_PARAMETER_FILE;
                break;
            case CONTAINERFILE:
                genericFileType = MinimalLanguageInterface.GenericFileType.CONTAINERFILE;
                break;
            default:
                LOG.error("Could not determine file type category for source file {}", file.getPath());
                throw new CustomWebApplicationException("Could not determine file type category for source file "
                    + file.getPath(), HttpStatus.SC_METHOD_FAILURE);
            }
            FileMetadata indexedFile = new FileMetadata(content, genericFileType, null);
            indexedFiles.put(absolutePath, indexedFile);
        }
        return indexedFiles;
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return new VersionTypeValidation(true, Collections.emptyMap());
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return new VersionTypeValidation(true, Collections.emptyMap());
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String filepath) {

        MinimalLanguageInterface.FileReader reader = new MinimalLanguageInterface.FileReader() {

            @Override
            public String readFile(String path) {
                return sourceCodeRepoInterface.readFile(repositoryId, path, version.getReference());
            }

            @Override
            public List<String> listFiles(String pathToDirectory) {
                return sourceCodeRepoInterface.listFiles(repositoryId, pathToDirectory, version.getReference());
            }
        };

        try {
            final Map<String, FileMetadata> stringPairMap = minimalLanguageInterface
                .indexWorkflowFiles(filepath, content, reader);
            Map<String, SourceFile> results = new HashMap<>();
            for (Map.Entry<String, FileMetadata> entry : stringPairMap.entrySet()) {

                // The language plugins don't necessarily set the file type
                // so query the plugin to find out what the imported file
                // type should be, because this is needed in downstream code.
                // We assume if imported files are not descriptors or not test files they are Dockerfiles,
                // however this may not be true for some languages, and we may have to change this
                DescriptorLanguage.FileType type;
                if (entry.getValue().genericFileType() == GenericFileType.IMPORTED_DESCRIPTOR) {
                    type = minimalLanguageInterface.getDescriptorLanguage().getFileType();
                } else if (entry.getValue().genericFileType() == GenericFileType.TEST_PARAMETER_FILE) {
                    type = minimalLanguageInterface.getDescriptorLanguage().getTestParamType();
                } else {
                    // For some languages this may be incorrect
                    type = FileType.DOCKERFILE;
                }
                if (minimalLanguageInterface.getDescriptorLanguage().isServiceLanguage()) {
                    // TODO: this needs to be more sophisticated
                    type = DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML;
                }
                String path = entry.getKey();
                String fileContent = entry.getValue().content();
                SourceFile sourceFile = SourceFile.limitedBuilder().type(type).content(fileContent).paths(path).build();
                sourceFile.getMetadata().setTypeVersion(entry.getValue().languageVersion());
                results.put(entry.getKey(), sourceFile);
            }
            return results;
        } catch (NullPointerException e) {
            // ignore, cannot assume plugins are well-behaved
            LOG.error("plugin threw NullPointer exception, dodging");
            return new HashMap<>();
        }
    }

    @Override
    public Optional<String> getContent(String mainDescriptorPath, String mainDescriptor, Set<SourceFile> secondarySourceFiles, Type type,
        ToolDAO dao) {

        if (type == Type.DAG && minimalLanguageInterface instanceof CompleteLanguageInterface) {
            final Map<String, Object> maps = ((CompleteLanguageInterface) minimalLanguageInterface)
                .loadCytoscapeElements(mainDescriptorPath, mainDescriptor, sourcefilesToIndexedFiles(secondarySourceFiles));
            return Optional.of(gson.toJson(maps));
        } else if (type == Type.TOOLS && minimalLanguageInterface instanceof CompleteLanguageInterface) {
            // TODO: hook up tools here for Galaxy
            List<CompleteLanguageInterface.RowData> rowData = new ArrayList<>();
            try {
                rowData = ((CompleteLanguageInterface) minimalLanguageInterface)
                    .generateToolsTable(mainDescriptorPath, mainDescriptor, sourcefilesToIndexedFiles(secondarySourceFiles));
            } catch (RuntimeException e) {
                LOG.error("could not parse tools from workflow", e);
                return Optional.empty();
            }
            final Map<String, DockerInfo> collect = rowData.stream()
                .collect(Collectors.toMap(row -> row.toolid,
                    row -> new DockerInfo("TBD".equals(row.filename) ? null : row.filename, "TBD".equals(row.dockerContainer) ? null : row.dockerContainer,
                        row.link == null ? null : row.link.toString())));
            return Optional.of(getJSONTableToolContent(collect));
        }
        return Optional.empty();
    }
}
