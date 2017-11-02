/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.common;

import java.io.File;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import ro.fortsoft.pf4j.PluginManager;
import org.junit.experimental.categories.Category;
import static org.junit.Assert.assertTrue;

import static io.dockstore.common.FileProvisionUtil.PLUGINS_JSON_FILENAME;

/**
 * @author gluu
 * @since 14/03/17
 */
public class FileProvisionUtilTest {
    @Test
    public void downloadPlugins() throws Exception {
        File iniFile = FileUtils.getFile("src", "test", "resources", "launcher.cwltool.ini");
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
