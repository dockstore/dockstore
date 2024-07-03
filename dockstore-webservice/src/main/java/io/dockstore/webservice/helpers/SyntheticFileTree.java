package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SyntheticFileTree implements FileTree {

    private Map<String, String> pathToContent = new HashMap<>();
    private static final String SLASH = "/";

    @Override
    public String readFile(String filePath) {
        return pathToContent.get(filePath);
    }

    @Override
    public List<String> listFiles(String dirPath) {
        String normalizedDirPath = addSlash(dirPath);
        return pathToContent.keySet().stream()
            .filter(path -> path.startsWith(normalizedDirPath))
            .map(path -> path.substring(normalizedDirPath.length()))
            .filter(path -> !path.isEmpty())
            .map(path -> path.split(SLASH)[0])
            .toList();
    }

    @Override
    public List<String> listPaths() {
        return new ArrayList<>(pathToContent.keySet());
    }

    public void addFile(String path, String content) {
        pathToContent.put(path, content);
    }

    private String addSlash(String dirPath) {
        return dirPath.endsWith(SLASH) ? dirPath : dirPath + SLASH;
    }
}
