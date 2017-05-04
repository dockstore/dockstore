package io.dockstore.common;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * @author gluu
 * @since 14/03/17
 */
public class PluginJSON {
    @SerializedName("name")
    @Expose
    private String name;

    @SerializedName("version")
    @Expose
    private String version;

    @SerializedName("location")
    @Expose
    private String location;

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }
}
