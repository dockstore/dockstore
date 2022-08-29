package io.dockstore.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class LanguageHandlerHelper {

    private LanguageHandlerHelper() {

    }

    /**
     * Resolves a relative path based on an absolute parent path
     * Should only be used with workflow imports and not for using
     * untrusted inputs for traversing the filesystem.
     * @param parentPath Absolute path to resolve
     * @param relativePath Relative path the parent file
     * @return Absolute version of relative path
     */
    public static String unsafeConvertRelativePathToAbsolutePath(String parentPath, String relativePath) {
        if (relativePath.startsWith("/")) {
            return relativePath;
        }

        Path workDir = Paths.get(parentPath); // lgtm[java/path-injection]
        Path workDirRoot = workDir.getRoot();
        if (workDirRoot == null) {
            throw new IllegalArgumentException("Expected an absolute path but got a relative path: " + parentPath);
        }

        // If the workDir is the root, leave it. If it is not the root, set workDir to the parent of parentPath
        workDir = !Objects.equals(parentPath, workDirRoot.toString()) ? workDir.getParent() : workDir;

        return workDir.resolve(relativePath).normalize().toString();
    }

}
