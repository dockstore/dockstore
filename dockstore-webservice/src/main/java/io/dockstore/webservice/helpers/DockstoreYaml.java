package io.dockstore.webservice.helpers;

import java.util.List;
import java.util.Map;

public class DockstoreYaml {

    public String dockstoreVersion;
    // clazz should be an enum, after figuring out how to read it in in ZipSourceFileHelper.readDockstoreYml
    public String clazz;
    public String primaryDescriptor;
    public List<String> testParameterFiles;
    public Map<String, String> metadata;
}
