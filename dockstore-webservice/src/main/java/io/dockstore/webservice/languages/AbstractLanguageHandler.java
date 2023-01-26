package io.dockstore.webservice.languages;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract methods that the LanguageHandlerInterface do not need to have (ex. specific to Dockstore and not plugins)
 * Used by CWLHandler and NextflowHandler
 */
public abstract class AbstractLanguageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLanguageHandler.class);

    /**
     *
     * @return  The file type that resolved imports will be automatically set to
     */
    protected abstract DescriptorLanguage.FileType getFileType();

    /**
     * Grabs a import file from Git based on its absolute path and add to imports mapping
     * @param repositoryId              identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param version                   version of the files to get
     * @param imports                   mapping of filenames to imports
     * @param givenImportPath           import path from CWL file
     * @param sourceCodeRepoInterface   used too retrieve imports
     * @param absoluteImportPath        absolute path of import in git repository
     */
    protected void handleImport(String repositoryId, Version version, Map<String, SourceFile> imports, String givenImportPath, SourceCodeRepoInterface sourceCodeRepoInterface, String absoluteImportPath) {
        sourceCodeRepoInterface.readFile(repositoryId, version, getFileType(), absoluteImportPath)
            .ifPresentOrElse(
                file -> {
                    file.setPath(givenImportPath);
                    imports.put(absoluteImportPath, file);
                },
                () -> LOG.error("Could not read: {}", absoluteImportPath)
            );
    }
}
