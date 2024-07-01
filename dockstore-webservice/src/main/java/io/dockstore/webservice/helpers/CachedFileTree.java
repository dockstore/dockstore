package io.dockstore.webservice.helpers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CachedFileTree implements FileTree {

    private Map<String, String> pathToContent = new HashMap<>();
    private Map<String, List<String>> pathToFiles = new HashMap<>();
    private List<String> paths;
    private FileTree fileTree;

    public CachedFileTree(FileTree fileTree) {
        this.fileTree = fileTree;
    }

    public String readFile(String filePath) {
        if (pathToContent.containsKey(filePath)) {
            return pathToContent.get(filePath);
        }
        String content = fileTree.readFile(filePath);
        pathToContent.put(filePath, content);
        return content;
    }

    public List<String> listFiles(String dirPath) {
        if (pathToFiles.containsKey(dirPath)) {
            return pathToFiles.get(dirPath);
        }
        List<String> files = fileTree.listFiles(dirPath);
        pathToFiles.put(dirPath, files);
        return files;
    }

    public List<String> listPaths() {
        if (paths == null) {
            paths = fileTree.listPaths();
        }
        return paths;
    }
}
