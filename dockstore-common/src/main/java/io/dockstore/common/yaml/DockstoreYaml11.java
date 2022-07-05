package io.dockstore.common.yaml;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * A POJO for version 1.1 of the Dockstore.yaml, which defined the
 * first version of services.
 * @since 1.7
 * @deprecated as of 1.9 in favor of DockstoreYaml12
 */
@Deprecated
public class DockstoreYaml11 implements DockstoreYaml {
    private String version;

    private YamlService11 service;

    @NotNull
    @Pattern(regexp = "1\\.1", message = "must be \"1.1\"")
    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    @NotNull
    public YamlService11 getService() {
        return service;
    }

    public void setService(final YamlService11 service) {
        this.service = service;
    }

}
