package io.dockstore.webservice.helpers;

import java.util.List;

public interface FileTree {

    String readFile(String path);
    List<String> listFiles(String pathToDirectory);
    List<String> listPaths();
}
