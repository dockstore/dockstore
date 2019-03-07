package io.dockstore.webservice.helpers;

import java.util.List;
import java.util.Map;

/**
 * A POJO holding data from .dockstore.yml.
 */
public class DockstoreYaml {

    public static final String VERSION = "1.0";
    public static final String CLAZZ = "workflow";

    /**
     * Required, expected to always be "1.0" for now.
     */
    public String dockstoreVersion;

    /**
     * Required. This should be an enum, after figuring out how to read it in in ZipSourceFileHelper.readAndPrevalidateDockstoreYml.
     *
     * Currently only "workflow" is the only value, but it other values will be coming.
     */
    public String clazz;

    /**
     * Required if the clazz is "workflow".
     */
    public String primaryDescriptor;
    /**
     * Optional if the clazz is "workflow".
     */
    public List<String> testParameterFiles;
    /**
     * Optional if the clazz is "workflow". A map of arbitrary name-value pairs.
     */
    public Map<String, String> metadata;
}
