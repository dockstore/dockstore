package io.dockstore.common;

import java.io.File;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import ro.fortsoft.pf4j.PluginManager;

import static io.dockstore.common.FileProvisionUtil.PLUGINS_JSON_FILENAME;
import static org.junit.Assert.*;

/**
 * @author gluu
 * @since 14/03/17
 */
public class FileProvisionUtilTest {
    @Test
    public void downloadPlugins() throws Exception {
        File iniFile = FileUtils.getFile("src", "test", "resources", "launcher.ini");
        INIConfiguration config = Utilities.parseConfig(iniFile.getAbsolutePath());
        FileProvisionUtil.downloadPlugins(config);
    }

    @Test
    public void createPluginJSONFile() throws Exception {
        String userHome = System.getProperty("user.home");
        String pluginFile = userHome + File.separator + ".dockstore" + File.separator + PLUGINS_JSON_FILENAME;
        FileProvisionUtil.createPluginJSONFile(pluginFile);
        File f = new File(pluginFile);
        assertTrue(f.exists() && !f.isDirectory());
        f.delete();
    }
}
