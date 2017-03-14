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

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
