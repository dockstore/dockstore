package io.dockstore.common.yaml;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * A POJO holding data from .dockstore.yml, version 1.0
 *
 * The API that uses this, HostedWorkflowResource.addZip, has been deprecated
 *
 * @deprecated since 1.9
 */

@Deprecated
public class DockstoreYaml10 implements DockstoreYaml {

    /**
     * Required, expected to always be "1.0" for now.
     */
    @NotNull(message = "Missing property \"dockstoreVersion\"")
    @Pattern(regexp = "1\\.0", message = "dockstoreVersion must be 1.0")
    public String dockstoreVersion;

    /**
     * Required. This should be an enum, after figuring out how to read it in in ZipSourceFileHelper.readAndPrevalidateDockstoreYml.
     *
     * Currently only "workflow" is the only value, but other values will be coming.
     */
    @NotNull(message = "Missing property \"class\"")
    @Pattern(regexp = "workflow", message = "The class property value must be \"workflow\"")
    public String clazz;

    /**
     * Required if the clazz is "workflow".
     */
    @NotNull(message = "Missing property \"primaryDescriptor\"")
    public String primaryDescriptor;
    /**
     * Optional if the clazz is "workflow".
     */
    public List<String> testParameterFiles;
    /**
     * Optional if the clazz is "workflow". A map of arbitrary name-value pairs.
     */
    public Map<String, String> metadata;

    @Override
    public String getVersion() {
        return dockstoreVersion;
    }
}
