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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides support for Jupyter .ipynb notebooks.
 * https://nbformat.readthedocs.io/en/latest/format_description.html
 * https://repo2docker.readthedocs.io/en/latest/specification.html
 */
public class IpynbHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(IpynbHandler.class);

    public static final Set<String> REES_FILES = Set.of("environment.yml", "Pipfile", "Pipfile.lock", "requirements.txt", "setup.py", "Project.toml", "REQUIRE", "install.R", "apt.txt", "DESCRIPTION", "postBuild", "start", "runtime.txt", "default.nix", "Dockerfile");
    public static final Set<String> REES_DIRS = Set.of("/", "/binder/", "/.binder/");

    @Override
    public Version parseWorkflowContent(String filePath, String content, Set<SourceFile> sourceFiles, Version version) {

        // Parse the notebook JSON.
        JSONObject notebook;
        try {
            notebook = new JSONObject(content);
        } catch (JSONException ex) {
            LOG.error("Could not parse notebook", ex);
            return version;
        }

        processAuthors(notebook, version);
        processRelease(notebook, version, filePath, sourceFiles);

        return version;
    }

    private void processAuthors(JSONObject notebook, Version version) {
        try {
            // This code will intentionally throw a JSONException if the "authors" field is not present.
            JSONArray jsonAuthors = notebook.getJSONObject("metadata").getJSONArray("authors");
            Set<Author> versionAuthors = new LinkedHashSet<>();
            for (int i = 0; i < jsonAuthors.length(); i++) {
                JSONObject jsonAuthor = jsonAuthors.getJSONObject(i);
                String name = jsonAuthor.optString("name", null);
                if (name != null && name.length() > 0) {
                    Author versionAuthor = new Author();
                    versionAuthor.setName(name);
                    versionAuthors.add(versionAuthor);
                }
                LOG.info("Notebook file contains author {}", name);
            }
            version.setAuthors(versionAuthors);
        } catch (JSONException ex) {
            LOG.warn("Could not extract notebook author information", ex);
        }
    }

    private void processRelease(JSONObject notebook, Version version, String notebookPath, Set<SourceFile> sourceFiles) {
        try {
            int formatMajor = notebook.getInt("nbformat");
            int formatMinor = notebook.getInt("nbformat_minor");
            String format = formatMajor + "." + formatMinor;
            sourceFiles.stream()
                .filter(file -> file.getAbsolutePath().equals(notebookPath))
                .forEach(file -> file.setTypeVersion(format));
            version.setDescriptorTypeVersionsFromSourceFiles(sourceFiles);
        } catch (JSONException ex) {
            LOG.warn("Could not extract notebook version information", ex);
        }
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String workingDirectoryForFile) {
        Map<String, SourceFile> pathsToFiles = new HashMap<>();

        // To avoid listing the contents of non-existent directories and generating non-cachable requests
        // https://github.com/dockstore/dockstore/pull/5329#discussion_r1088120869
        // we first determine the contents of '/'
        Set<String> rootNames = new HashSet<>(ObjectUtils.firstNonNull(sourceCodeRepoInterface.listFiles(repositoryId, "/", version.getReference()), List.of()));

        // For each possible REES directory:
        for (String reesDir: REES_DIRS) {
            // Confirm the directory [probably] exists.
            if ("/".equals(reesDir) || rootNames.contains(reesDir.replace("/", ""))) {
                // List the files in the directory.
                List<String> names = sourceCodeRepoInterface.listFiles(repositoryId, reesDir, version.getReference());
                if (names != null) {
                    // Check each file in the directory.
                    for (String name: names) {
                        // If it's a REES file, read it into a SourceFile and add it to the map.
                        if (REES_FILES.contains(name)) {
                            sourceCodeRepoInterface.readFile(repositoryId, version, DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_REES, reesDir + name)
                                .ifPresent(file -> pathsToFiles.put(file.getAbsolutePath(), file));
                        }
                    }
                }
            }
        }
        return pathsToFiles;
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
        Optional<SourceFile> file = sourceFiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getPath(), notebookPath))).findFirst();
        if (!file.isPresent()) {
            return negativeValidation(notebookPath, "No notebook file is present");
        }
        String content = file.get().getContent();

        // Parse the notebook JSON.
        JSONObject notebook;
        try {
            notebook = new JSONObject(content);
        } catch (JSONException ex) {
            return negativeValidation(notebookPath, "The notebook file is not valid JSON", ex);
        }

        // Confirm the existence and typedness of the fields that should always be present.
        try {
            checkEssentialFields(notebook);
        } catch (JSONException ex) {
            return negativeValidation(notebookPath, "The notebook file is malformed", ex);
        }

        // Check that the entry's programming language (descriptor language subclass) matches the notebook's programming language.
        try {
            String entryLanguage = workflow.getDescriptorTypeSubclass().toString().toLowerCase();
            String notebookLanguage = extractProgrammingLanguage(notebook).toLowerCase();
            if (!Objects.equals(entryLanguage, notebookLanguage)) {
                return negativeValidation(notebookPath, String.format("The notebook programming language must be '%s'", entryLanguage));
            }
        } catch (JSONException ex) {
            return negativeValidation(notebookPath, "Error reading the notebook programming language", ex);
        }

        // If we reach this point, everything is well, return a positive validation.
        return positiveValidation();
    }

    private void checkEssentialFields(JSONObject notebook) throws JSONException {
        notebook.getJSONObject("metadata");
        notebook.getInt("nbformat");
        notebook.getInt("nbformat_minor");
        notebook.getJSONArray("cells");
    }

    private String extractProgrammingLanguage(JSONObject notebook) throws JSONException {
        JSONObject metadata = notebook.getJSONObject("metadata");
        // If key "language_info" is present, the spec says that it must contain the key "name".
        if (metadata.has("language_info")) {
            return metadata.getJSONObject("language_info").optString("name", "python");
        }
        return "python";
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath) {
        throw new UnsupportedOperationException("Notebooks do not support tools");
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        throw new UnsupportedOperationException("Notebooks do not support test files");
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
}
