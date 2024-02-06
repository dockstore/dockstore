package io.dockstore.common.yaml;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

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
    @NotNull
    @Pattern(regexp = "1\\.0", message = "must be \"1.0\"")
    public String dockstoreVersion;

    /**
     * Required. This should be an enum, after figuring out how to read it in in ZipSourceFileHelper.readAndPrevalidateDockstoreYml.
     *
     * Currently only "workflow" is the only value, but other values will be coming.
     */
    @NotNull
    @Pattern(regexp = "workflow")
    public String clazz;

    /**
     * Required if the clazz is "workflow".
     */
    @NotNull
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
