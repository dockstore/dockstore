// TODO add copyright header

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides support for Jupyter .ipynb notebooks.
 * https://nbformat.readthedocs.io/en/latest/format_description.html
 */
public class IpynbHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(IpynbHandler.class);

    @Override
    public Version parseWorkflowContent(String filePath, String content, Set<SourceFile> sourceFiles, Version version) {

        // Extract the authors from the metadata.
        try {
            JSONArray jsonAuthors = new JSONObject(content).getJSONObject("metadata").getJSONArray("authors");
            Set<Author> versionAuthors = new LinkedHashSet<>();
            for (int i = 0; i < jsonAuthors.length(); i++) {
                JSONObject jsonAuthor = jsonAuthors.getJSONObject(i);
                String name = jsonAuthor.optString("name", null);
                String email = jsonAuthor.optString("email", null);
                if (name != null || email != null) {
                    Author versionAuthor = new Author();
                    versionAuthor.setName(name);
                    versionAuthor.setEmail(email);
                    versionAuthors.add(versionAuthor);
                }
                LOG.info("Notebook file contains author {} {}", name, email);
            }
            version.setAuthors(versionAuthors);
        } catch (JSONException ex) {
            // Ignore the error extracting the author names.
            // TODO maybe do something different.
            LOG.warn("Could not extract notebook author information", ex);
        }
        return version;
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String workingDirectoryForFile) {

        // Read any REES configuration files.
        // Per the spec, REES files reside in the following directories: /, /binder, /.binder
        // https://repo2docker.readthedocs.io/en/latest/specification.html
        final List<String> reesNames = List.of("environment.yml", "Pipfile", "Pipfile.lock", "requirements.txt", "setup.py", "Project.toml", "REQUIRE", "install.R", "apt.txt", "DESCRIPTION", "postBuild", "start", "runtime.txt", "default.nix", "Dockerfile");
        final List<String> reesDirs = List.of("/", "/binder/", "/.binder/");

        // For each possible REES directory, list the directory contents, and if we find configuration files, read them.
        Map<String, SourceFile> pathsToFiles = new HashMap<>();
        for (String reesDir: reesDirs) {
            List<String> paths = sourceCodeRepoInterface.listFiles(repositoryId, reesDir, version.getReference());
            if (paths != null) {
                for (String path: paths) {
                    for (String reesName: reesNames) {
                        if (path.endsWith("/" + reesName)) {
                            Optional<SourceFile> file = sourceCodeRepoInterface.readFile(repositoryId, version, DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_REES, path);
                            if (file.isPresent()) {
                                pathsToFiles.put(file.get().getAbsolutePath(), file.get());
                            }
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

        // Notebooks don't have a DAG
        // When we add an "image" reference to the notebook version, figure out a way to return it in the list of tools here, or maybe elsewhere.
        throw new UnsupportedOperationException("Notebooks do not have DAGs or tools");
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourceFiles, String notebookPath, Workflow workflow) {

        // Determine the content of the notebook file.
        Optional<SourceFile> notebookFile = sourceFiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getPath(), notebookPath))).findFirst();
        if (!notebookFile.isPresent()) {
            return negativeValidation(notebookPath, "No notebook file is present");
        }
        String content = notebookFile.get().getContent();

        // Parse the notebook JSON and check for the existence of some fields.
        JSONObject notebook;
        JSONObject metadata;
        int formatMajor;
        int formatMinor;
        JSONArray cells;
        try {
            notebook = new JSONObject(content);
            metadata = notebook.getJSONObject("metadata");
            formatMajor = notebook.getInt("nbformat");
            formatMinor = notebook.getInt("nbformat_minor");
            cells = notebook.getJSONArray("cells");
        } catch (JSONException ex) {
            return negativeValidation(notebookPath, "The notebook file is malformed", ex);
        }

        // Check that the entry's programming language (descriptor language subclass) matches the notebook's programming language.
        try {
            String entryLanguage = workflow.getDescriptorTypeSubclass().toString().toLowerCase();
            String notebookLanguage;
            // If "language_info" is present, the spec says that it must contain a key named "name".
            if (metadata.has("language_info")) {
                notebookLanguage = metadata.getJSONObject("language_info").getString("name").toLowerCase();
            } else {
                notebookLanguage = "python";
            }
            if (!Objects.equals(entryLanguage, notebookLanguage)) {
                return negativeValidation(notebookPath, String.format("The notebook programming language must be '%s'", entryLanguage));
            }
        } catch (JSONException ex) {
            return negativeValidation(notebookPath, "Error reading the notebook programming language", ex);
        }

        // If we reach this point, everything is well, return a positive validation.
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

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath) {
        // TODO is this ok?
        throw new UnsupportedOperationException("Notebooks do not support tools");
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        // TODO is this ok?
        throw new UnsupportedOperationException("Notebooks do not support test files");
    }
}
