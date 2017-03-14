package io.dockstore.common;

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * @author gluu
 * @since 14/03/17
 */
public class PluginsJSON {
    @SerializedName("plugins")
    @Expose
    private List<PluginJSON> plugins;

    public List<PluginJSON> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginJSON> plugins) {
        this.plugins = plugins;
    }
}
