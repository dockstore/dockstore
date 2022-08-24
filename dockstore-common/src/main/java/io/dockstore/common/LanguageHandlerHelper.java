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
     * @param parentPath Absolute path to parent file
     * @param relativePath Relative path to another file
     * @return Absolute version of relative path
     */
    public static String unsafeConvertRelativePathToAbsolutePath(String parentPath, String relativePath) {
        if (relativePath.startsWith("/")) {
            return relativePath;
        }
        // If the parent path isn't absolute, make it so, assuming that it's relative to the root directory.
        if (!parentPath.startsWith("/")) {
            parentPath = Paths.get("/").resolve(parentPath).normalize().toString();
        }

        Path workDir = Paths.get(parentPath); // lgtm[java/path-injection]

        // If the workDir is the root, leave it. If it is not the root, set workDir to the parent of parentPath
        workDir = !Objects.equals(parentPath, workDir.getRoot().toString()) ? workDir.getParent() : workDir;

        return workDir.resolve(relativePath).normalize().toString();
    }
}
