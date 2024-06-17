package io.dockstore.webservice.helpers;

import java.util.List;

public interface FileTree {

    public String readFile(String path);
    public List<String> listFiles(String pathToDirectory);
    public List<String> listPaths();
}
