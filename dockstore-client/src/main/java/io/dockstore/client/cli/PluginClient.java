/*
 *    Copyright 2016 OICR
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
package io.dockstore.client.cli;

import java.util.List;

import io.dockstore.common.FileProvisionUtil;
import io.dockstore.common.TabExpansionUtil;
import org.apache.commons.configuration2.INIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginWrapper;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;

/**
 *
 */
public final class PluginClient {

    private static final Logger LOG = LoggerFactory.getLogger(PluginClient.class);


    private PluginClient() {
        // disable constructor for utility class
    }

    /**
     *
     * @param args
     * @param configFile
     */
    public static boolean handleCommand(List<String> args, INIConfiguration configFile) {
        String cmd = args.size() > 0 ? args.remove(0) : null;
        // see if this is a general command

        if (null != cmd) {
            switch (cmd) {
            case "list":
                PluginManager pluginManager = FileProvisionUtil.getPluginManager(configFile);
                List<PluginWrapper> plugins = pluginManager.getStartedPlugins();
                StringBuilder builder = new StringBuilder();
                builder.append("PluginId\tPlugin Version\tPlugin Path\n");
                for (PluginWrapper plugin : plugins) {
                    builder.append(plugin.getPluginId());
                    builder.append("\t");
                    builder.append(plugin.getPlugin().getWrapper().getDescriptor().getVersion());
                    builder.append("\t");
                    builder.append(FileProvisionUtil.getFilePluginLocation(configFile) + plugin.getPlugin().getWrapper().getPluginPath() + "(.zip)");
                    builder.append("\n");
                }
                out(TabExpansionUtil.aligned(builder.toString()));
                return true;
            case "download":
                FileProvisionUtil.downloadPlugins(configFile);
                return true;
            default:
                // fall through
            }
        }
        printHelpHeader();
        out("");
        out("Commands:");
        out("");
        out("  list                 :  List currently activated file provisioning plugins");
        out("");
        out("  download             :  Download default file provisioning plugins");
        return true;
    }
}
