package io.dockstore.webservice.languages;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SourceFileHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SourceFileHelper.class);

    private SourceFileHelper() {
    }

    /**
     * Reads a file from the specified source code control system.
     */
    public static SourceFile readFile(String repositoryId, Version version, String path, SourceCodeRepoInterface sourceCodeRepoInterface, String absolutePath, DescriptorLanguage.FileType fileType) {
        // create a new source file
        final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(repositoryId, fileType, version, absolutePath);
        if (fileResponse == null) {
            LOG.error("Could not read: " + absolutePath);
            return null;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(path);
        sourceFile.setAbsolutePath(absolutePath);
        return sourceFile;
    }
}
