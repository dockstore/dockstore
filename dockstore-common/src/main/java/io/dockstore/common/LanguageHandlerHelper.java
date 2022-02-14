package io.dockstore.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class LanguageHandlerHelper {

    private LanguageHandlerHelper() {

    }

    /**
     * Resolves a relative path based on an absolute parent path
     * @param parentPath Absolute path to parent file
     * @param relativePath Relative path the parent file
     * @return Absolute version of relative path
     */
    public static String convertRelativePathToAbsolutePath(String parentPath, String relativePath) {
        if (relativePath.startsWith("/")) {
            return relativePath;
        }

        Path workDir = Paths.get(parentPath); // lgtm[java/path-injection]

        // If the workDir is the root, leave it. If it is not the root, set workDir to the parent of parentPath
        workDir = !Objects.equals(parentPath, workDir.getRoot().toString()) ? workDir.getParent() : workDir;

        return workDir.resolve(relativePath).normalize().toString();
    }

}
