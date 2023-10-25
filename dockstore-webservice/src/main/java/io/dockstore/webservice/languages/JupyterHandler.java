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

package io.dockstore.webservice.languages;

import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_DEVCONTAINER;
import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_REES;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides support for Jupyter .ipynb notebooks.
 * https://nbformat.readthedocs.io/en/latest/format_description.html
 * https://repo2docker.readthedocs.io/en/latest/specification.html
 */
public class JupyterHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(JupyterHandler.class);

    public static final Set<String> REES_FILES = Set.of("environment.yml", "Pipfile", "Pipfile.lock", "requirements.txt", "setup.py", "Project.toml", "REQUIRE", "install.R", "apt.txt", "DESCRIPTION", "postBuild", "start", "runtime.txt", "default.nix", "Dockerfile");
    public static final Set<String> REES_DIRS = Set.of("/", "/binder/", "/.binder/");

    private static final String PYTHON = "python";
    private static final int FOUR = 4;

    @Override
    public Version parseWorkflowContent(String filePath, String content, Set<SourceFile> sourceFiles, Version version) {

        Nbformat notebook;
        try {
            notebook = parseNotebook(content);
        } catch (JsonParseException ex) {
            LOG.error("Could not parse notebook", ex);
            return version;
        }

        processAuthors(notebook, version);
        processRelease(notebook, version, filePath, sourceFiles);

        return version;
    }

    private Nbformat parseNotebook(String content) throws JsonParseException {
        Nbformat notebook = new GsonBuilder().create().fromJson(content, Nbformat.class);
        if (notebook == null) {
            throw new JsonParseException("Notebook does not contain any content.");
        }
        if (notebook.getMetadata() == null) {
            throw new JsonParseException("Notebook is missing the 'metadata' field");
        }
        if (findCells(notebook) == null) {
            throw new JsonParseException("Notebook is missing the 'cells' field");
        }
        if (notebook.getFormatMajor() == null || notebook.getFormatMinor() == null) {
            throw new JsonParseException("Notebook format fields are missing or malformed");
        }
        return notebook;
    }

    private List<Nbformat.Cell> findCells(Nbformat notebook) {
        // Notebooks with major version 4 have a root-level "cells" property.
        // Older notebooks have a root-level "worksheets" property, consisting of a list of worksheets, each of which contains a "cells" field.
        // According to this link, older notebook environments had "no UI to support multiple worksheets":
        // https://github.com/ipython/ipython/wiki/IPEP-17%3a-Notebook-Format-4
        // So, if the major version < 4 and a list of worksheets exists, return the cells from the first one:
        if (notebook.getFormatMajor() != null && notebook.getFormatMajor() < FOUR
            && notebook.getWorksheets() != null && notebook.getWorksheets().size() > 0) {
            return notebook.getWorksheets().get(0).getCells();
        }
        return notebook.getCells();
    }

    private void processAuthors(Nbformat notebook, Version version) {
        List<Nbformat.Metadata.Author> authors = notebook.getMetadata().getAuthors();
        if (authors != null) {
            version.setAuthors(authors.stream()
                .map(Nbformat.Metadata.Author::getName)
                .filter(StringUtils::isNotEmpty)
                .map(name -> {
                    Author versionAuthor = new Author();
                    versionAuthor.setName(name);
                    LOG.info("Notebook file contains author '{}'", name);
                    return versionAuthor;
                })
                .collect(Collectors.toSet())
            );
        }
    }

    private void processRelease(Nbformat notebook, Version version, String notebookPath, Set<SourceFile> sourceFiles) {
        Integer formatMajor = notebook.getFormatMajor();
        Integer formatMinor = notebook.getFormatMinor();
        String format = formatMajor + "." + formatMinor;
        sourceFiles.stream()
            .filter(file -> file.getAbsolutePath().equals(notebookPath))
            .forEach(file -> file.getMetadata().setTypeVersion(format));
        version.setDescriptorTypeVersionsFromSourceFiles(sourceFiles);
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String workingDirectoryForFile) {
        Map<String, SourceFile> pathsToFiles = new HashMap<>();

        // The following are some helpers:
        // listFiles reads the names of the files/directories from the specified path in the repo ref, empty if the path doesn't exist
        Function<String, Set<String>> listFiles = path ->
            new HashSet<>(ObjectUtils.firstNonNull(sourceCodeRepoInterface.listFiles(repositoryId, path, version.getReference()), List.of()));
        // fileReader reads the file at the specified path in the repo ref, turns it into a sourcefile, and puts it into the result Map
        BiConsumer<String, DescriptorLanguage.FileType> fileReader = (path, type) ->
            sourceCodeRepoInterface.readFile(repositoryId, version, type, path)
                .ifPresent(file -> pathsToFiles.put(file.getAbsolutePath(), file));

        // To avoid listing the contents of non-existent directories and generating non-cachable requests
        // (https://github.com/dockstore/dockstore/pull/5329#discussion_r1088120869),
        // the following code tends to list the files in the directories of interest and scan through them,
        // rather than probing single paths.
        Set<String> rootNames = listFiles.apply("/");

        // Read the REES configuration files
        // For each possible REES directory, check to see if it contains any REES files
        for (String reesDir: REES_DIRS) {
            // Confirm the directory [probably] exists.
            if ("/".equals(reesDir) || rootNames.contains(reesDir.replace("/", ""))) {
                listFiles.apply(reesDir).stream()
                    .filter(REES_FILES::contains)
                    .forEach(name -> fileReader.accept(reesDir + name, DOCKSTORE_NOTEBOOK_REES));
            }
        }

        // Scan for and read the devcontainer files of highest precedence
        // Relevant section of devcontainer spec: https://containers.dev/implementors/spec/#devcontainerjson
        if (rootNames.contains(".devcontainer.json")) {
            // Read /.devcontainer.json
            fileReader.accept("/" + ".devcontainer.json", DOCKSTORE_NOTEBOOK_DEVCONTAINER);
        } else if (rootNames.contains(".devcontainer")) {
            String path = "/" + ".devcontainer";
            Set<String> names = listFiles.apply(path);
            if (names.contains("devcontainer.json")) {
                // Read /.devcontainer/devcontainer.json
                fileReader.accept(path + "/" + "devcontainer.json", DOCKSTORE_NOTEBOOK_DEVCONTAINER);
            } else {
                // Scan for and read all files with path /.devcontainer/<folder>/devcontainer.json
                names.stream().forEach(folder -> {
                    String folderPath = path + "/" + folder;
                    listFiles.apply(folderPath).stream()
                        .filter("devcontainer.json"::equals)
                        .forEach(name -> fileReader.accept(folderPath + "/" + name, DOCKSTORE_NOTEBOOK_DEVCONTAINER));
                });
            }
        }

        return pathsToFiles;
    }

    private Set<String> listFiles(String path, SourceCodeRepoInterface sourceCodeRepoInterface, String repositoryId, Version version) {
        return new HashSet<>(ObjectUtils.firstNonNull(sourceCodeRepoInterface.listFiles(repositoryId, path, version.getReference()), List.of()));
    }

    @Override
    public Map<String, SourceFile> processUserFiles(String repositoryId, List<String> paths, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, Set<String> excludePaths) {
        return sourceCodeRepoInterface.readPaths(repositoryId, version, DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_OTHER, excludePaths, paths).stream()
            .collect(Collectors.toMap(SourceFile::getAbsolutePath, Function.identity()));
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public Optional<String> getContent(String mainDescriptorPath, String mainDescriptor, Set<SourceFile> secondarySourceFiles, LanguageHandlerInterface.Type type, ToolDAO dao) {
        return Optional.empty();
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourceFiles, String notebookPath, Workflow workflow) {

        // Determine the content of the notebook file.
        Optional<SourceFile> file = sourceFiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getAbsolutePath(), notebookPath))).findFirst();
        if (!file.isPresent()) {
            return negativeValidation(notebookPath, "No notebook file is present");
        }
        String content = file.get().getContent();

        // Parse the notebook.
        Nbformat notebook;
        try {
            notebook = parseNotebook(content);
        } catch (JsonParseException ex) {
            return negativeValidation(notebookPath, "The notebook file is malformed", ex);
        }

        // Confirm that the entry's programming language (descriptor type subclass) matches the notebook's programming language.
        try {
            String entryLanguage = workflow.getDescriptorTypeSubclass().toString();
            String notebookLanguage = extractProgrammingLanguage(notebook);
            if (!entryLanguage.equalsIgnoreCase(notebookLanguage)) {
                return negativeValidation(notebookPath, String.format("The notebook programming language must be '%s'", entryLanguage));
            }
        } catch (JsonParseException ex) {
            return negativeValidation(notebookPath, "Error reading the notebook programming language", ex);
        }

        // If we reach this point, everything is well, return a positive validation.
        return positiveValidation();
    }

    private String extractProgrammingLanguage(Nbformat notebook) {
        return notebook.getMetadata().optLanguageInfo().flatMap(Nbformat.Metadata.LanguageInfo::optName).orElse(PYTHON);
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath) {
        throw new UnsupportedOperationException("Notebooks do not support tools");
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        // For now, until we determine the type(s) of test files we should be validating, return without inspecting the files.
        return positiveValidation();
    }

    private VersionTypeValidation positiveValidation() {
        LOG.info("Created positive validation");
        return new VersionTypeValidation(true, Collections.emptyMap());
    }

    private VersionTypeValidation negativeValidation(String path, String message) {
        LOG.warn("Created negative validation, file {}: {}", path, message);
        return new VersionTypeValidation(false, Map.of(path, message));
    }

    private VersionTypeValidation negativeValidation(String path, String message, Exception ex) {
        String reason = ex.getMessage();
        if (reason != null) {
            message = String.format("%s: %s", message, reason);
        }
        LOG.warn("Created negative validation, file {}: {}", path, message, ex);
        return new VersionTypeValidation(false, Map.of(path, message));
    }

    /**
     * Partial read-only POJO representation of a parsed notebook file.
     */
    public static class Nbformat {

        @SerializedName("nbformat")
        private Integer formatMajor;

        @SerializedName("nbformat_minor")
        private Integer formatMinor;

        @SerializedName("metadata")
        private Metadata metadata;

        @SerializedName("cells")
        private List<Cell> cells;

        @SerializedName("worksheets")
        private List<Worksheet> worksheets;

        public Integer getFormatMajor() {
            return formatMajor;
        }

        public Integer getFormatMinor() {
            return formatMinor;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public List<Cell> getCells() {
            return cells;
        }

        public List<Worksheet> getWorksheets() {
            return worksheets;
        }

        public static class Metadata {

            @SerializedName("authors")
            private List<Author> authors;

            @SerializedName("language_info")
            private LanguageInfo languageInfo;

            public List<Author> getAuthors() {
                return authors;
            }

            public Optional<List<Author>> optAuthors() {
                return Optional.ofNullable(getAuthors());
            }

            public LanguageInfo getLanguageInfo() {
                return languageInfo;
            }

            public Optional<LanguageInfo> optLanguageInfo() {
                return Optional.ofNullable(getLanguageInfo());
            }

            public static class Author {

                @SerializedName("name")
                private String name;

                private String getName() {
                    return name;
                }

                private Optional<String> optName() {
                    return Optional.ofNullable(getName());
                }
            }

            public static class LanguageInfo {

                @SerializedName("name")
                private String name;

                private String getName() {
                    return name;
                }

                private Optional<String> optName() {
                    return Optional.ofNullable(getName());
                }
            }
        }

        public static class Cell {
        }

        public static class Worksheet {

            @SerializedName("cells")
            private List<Cell> cells;

            public List<Cell> getCells() {
                return cells;
            }
        }
    }
}
