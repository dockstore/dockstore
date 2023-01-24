package io.dockstore.webservice.languages;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract methods that the LanguageHandlerInterface do not need to have (ex. specific to Dockstore and not plugins)
 * Used by CWLHandler and NextflowHandler
 */
public abstract class AbstractLanguageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLanguageHandler.class);

    /**
     * Grabs a import file from Git based on its absolute path and add to imports mapping
     * @param repositoryId              identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param version                   version of the files to get
     * @param imports                   mapping of filenames to imports
     * @param givenImportPath           import path from CWL file
     * @param sourceCodeRepoInterface   used too retrieve imports
     * @param absoluteImportPath        absolute path of import in git repository
     */
    protected void handleImport(String repositoryId, Version version, Map<String, SourceFile> imports, String givenImportPath, SourceCodeRepoInterface sourceCodeRepoInterface, String absoluteImportPath, DescriptorLanguage.FileType fileType) {
        final SourceFile sourceFile = readFile(repositoryId, version, givenImportPath, sourceCodeRepoInterface, absoluteImportPath, fileType);
        if (sourceFile != null) {
            imports.put(absoluteImportPath, sourceFile);
        }
    }

    protected SourceFile readFile(String repositoryId, Version version, String givenPath, SourceCodeRepoInterface sourceCodeRepoInterface, String absolutePath, DescriptorLanguage.FileType fileType) {
        final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(repositoryId, fileType, version, absolutePath);
        if (fileResponse == null) {
            LOG.error("Could not read file: " + absolutePath);
            return null;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(givenPath);
        sourceFile.setAbsolutePath(absolutePath);
        return sourceFile;
    }

    protected List<SourceFile> readPath(String repositoryId, Version version, String givenPath, SourceCodeRepoInterface sourceCodeRepoInterface, String absolutePath, Set<String> excludePaths, DescriptorLanguage.FileType fileType) {
        if (excludePaths.contains(absolutePath)) {
            return List.of();
        }
        SourceFile file = readFile(repositoryId, version, givenPath, sourceCodeRepoInterface, absolutePath, fileType);
        if (file != null) {
            return List.of(file);
        }
        List<String> paths = sourceCodeRepoInterface.listFiles(repositoryId, absolutePath, version.getReference());
        if (paths != null) {
            return readPaths(repositoryId, version, paths, sourceCodeRepoInterface, excludePaths, fileType);
        }
        LOG.error("Could not read path: " + absolutePath);
        return List.of();
    }

    protected List<SourceFile> readFiles(String repositoryId, Version version, List<String> paths, SourceCodeRepoInterface sourceCodeRepoInterface, Set<String> excludePaths, DescriptorLanguage.FileType fileType) {
        return paths.stream()
            .filter(path -> !excludePaths.contains(path))
            .map(path -> readFile(repositoryId, version, path, sourceCodeRepoInterface, path, fileType))
            .filter(Objects::nonNull)
            .toList();
    }

    protected List<SourceFile> readPaths(String repositoryId, Version version, List<String> paths, SourceCodeRepoInterface sourceCodeRepoInterface, Set<String> excludePaths, DescriptorLanguage.FileType fileType) {
        return paths.stream().flatMap(path -> readPath(repositoryId, version, path, sourceCodeRepoInterface, path, excludePaths, fileType).stream()).toList();
    }
}
