// TODO add copyright header

package io.dockstore.webservice.languages;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.http.HttpStatus;
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
        // Parse the notebook.
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
            LOG.info("Notebook file is malformed " + ex.getMessage());
            // TODO add negative validation
            return version;
        }

        /*
        // Check that the entry's programming language (descriptor language subclass) matches
        // the programming language of the notebook.
        try {
            String entryLanguage = version.getParent().getDescriptorSubclass();
            String notebookLanguage;
            // If "language_info" is present, it must contain a key named "name".
            if (metadata.has("language_info")) {
                notebookLanguage = metadata.getJSONObject("language_info").getString("name");
            } else {
                notebookLanguage = "python";
            }
            if (!Objects.equals(entryLanguage, notebookLanguage)) {
                // TODO add negative validation
            }
        } catch (JSONException ex) {
            // TODO add negative validation
            return version;
        }
        */

        // Extract the authors from the metadata.
        try {
            JSONArray authors = notebook.getJSONObject("metadata").getJSONArray("authors");
            for (int i = 0; i < authors.length(); i++) {
                JSONObject author = authors.getJSONObject(i);
                String name = author.getString("name");
                LOG.info("Notebook file contains name " + name);
            }
        } catch (JSONException ex) {
            // Ignore the error extracting the author names.
        }

        return version;
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String workingDirectoryForFile) {
        Map<String, SourceFile> pathsToFiles = new HashMap<>();
        for (String reesDir: new String[]{ "/", "/binder/", "/.binder/" }) {
            for (String reesName: new String[]{ "requirements.txt" }) {
                String reesPath = reesDir + reesName;
                SourceFile file = SourceFileHelper.readFile(repositoryId, version, reesPath, sourceCodeRepoInterface, reesPath, DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_REES);
                if (file != null) {
                    pathsToFiles.put(file.getAbsolutePath(), file);
                }
            }
        }
        return pathsToFiles;
    }

    @Override
    public Map<String, SourceFile> processOtherFiles(String repositoryId, List<String> otherFilePaths, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, Map<String, SourceFile> existingPathsToFiles) {

        Map<String, SourceFile> otherPathsToFiles = new HashMap<>(existingPathsToFiles);
        for (String path: otherFilePaths) {
            if (!otherPathsToFiles.containsKey(path) && !existingPathsToFiles.containsKey(path)) {
                SourceFile file = SourceFileHelper.readFile(repositoryId, version, path, sourceCodeRepoInterface, path, DescriptorLanguage.FileType.DOCKSTORE_NOTEBOOK_OTHER);
                if (file != null) {
                    otherPathsToFiles.put(file.getAbsolutePath(), file);
                }
            }
        }
        return otherPathsToFiles;
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public Optional<String> getContent(String mainDescriptorPath, String mainDescriptor, Set<SourceFile> secondarySourceFiles, LanguageHandlerInterface.Type type, ToolDAO dao) {
        try {
            // TODO notebooks don't have a DAG
            // TODO return notebook "image" is type is TOOL
            return Optional.of("");
        } catch (Exception ex) {
            final String exMsg = "Notebook parse error: " + ex.getMessage();
            LOG.error(exMsg, ex);
            throw new CustomWebApplicationException(exMsg, HttpStatus.SC_UNPROCESSABLE_ENTITY);
        }
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath) {
        // TODO check if parseable and well-formed
        // TODO does there exist notebook validation code?
        return null;
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourceFiles, String primaryDescriptorFilePath) {
        throw new UnsupportedOperationException("Notebooks do not support tools");
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        if (!sourceFiles.isEmpty()) {
            throw new UnsupportedOperationException("Notebooks do not support test files");
        }
        // TODO
        return null;
    }
}
