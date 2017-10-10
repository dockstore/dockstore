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
package io.dockstore.client.cli;

import java.util.List;

import avro.shaded.com.google.common.base.Joiner;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import io.dockstore.common.FileProvisionUtil;
import io.dockstore.common.TabExpansionUtil;
import io.dockstore.provision.ProvisionInterface;
import org.apache.commons.configuration2.INIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginWrapper;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;

/**
 *
 */
public final class PluginClient {

    private static final Logger LOG = LoggerFactory.getLogger(PluginClient.class);

    private PluginClient() {
        // disable constructor for utility class
    }

    /**
     * This function is used when nesting commands within commands (ex. list and download within plugin)
     *
     * @param parentCommand The parent command (ex. plugin)
     * @param commandName   The nested command name (ex. "list")
     * @param commandObject The nested command (ex. list)
     * @return
     */
    private static JCommander addCommand(JCommander parentCommand, String commandName, Object commandObject) {
        parentCommand.addCommand(commandName, commandObject);
        return parentCommand.getCommands().get(commandName);
    }

    /**
     * @param args
     * @param configFile
     */
    public static boolean handleCommand(List<String> args, INIConfiguration configFile) {
        String[] argv = args.toArray(new String[args.size()]);
        JCommander jc = new JCommander();

        CommandPlugin commandPlugin = new CommandPlugin();
        JCommander jcPlugin = addCommand(jc, "plugin", commandPlugin);

        CommandPluginList commandPluginList = new CommandPluginList();
        addCommand(jcPlugin, "list", commandPluginList);

        CommandPluginDownload commandPluginDownload = new CommandPluginDownload();
        addCommand(jcPlugin, "download", commandPluginDownload);
        // Not parsing with jc because we know the first command was plugin.  jc's purpose is to display help
        jcPlugin.parse(argv);
        try {
            if (args.isEmpty() || commandPlugin.help) {
                printJCommanderHelp(jc, "dockstore", "plugin");
            } else {
                switch (jcPlugin.getParsedCommand()) {
                case "list":
                    if (commandPluginList.help) {
                        printJCommanderHelp(jc, "dockstore", "plugin");
                    } else {
                        return handleList(configFile);
                    }
                    break;
                case "download":
                    if (commandPluginDownload.help) {
                        printJCommanderHelp(jc, "dockstore", "plugin");
                    } else {
                        return handleDownload(configFile);
                    }
                    break;
                default:
                    // fall through
                }
            }
        } catch (ParameterException e) {
            printJCommanderHelp(jc, "dockstore", "plugin");
        }
        return true;

    }

    private static boolean handleList(INIConfiguration configFile) {
        PluginManager pluginManager = FileProvisionUtil.getPluginManager(configFile);
        List<PluginWrapper> plugins = pluginManager.getStartedPlugins();
        StringBuilder builder = new StringBuilder();
        builder.append("PluginId\tPlugin Version\tPlugin Path\tSchemes handled\n");
        for (PluginWrapper plugin : plugins) {
            builder.append(plugin.getPluginId());
            builder.append("\t");
            builder.append(plugin.getPlugin().getWrapper().getDescriptor().getVersion());
            builder.append("\t");
            builder.append(FileProvisionUtil.getFilePluginLocation(configFile)).append(plugin.getPlugin().getWrapper().getPluginPath())
                    .append("(.zip)");
            builder.append("\t");
            List<ProvisionInterface> extensions = pluginManager.getExtensions(ProvisionInterface.class, plugin.getPluginId());
            extensions.forEach(extension -> Joiner.on(',').appendTo(builder, extension.schemesHandled()));
            builder.append("\n");
        }
        out(TabExpansionUtil.aligned(builder.toString()));
        return true;
    }

    private static boolean handleDownload(INIConfiguration configFile) {
        FileProvisionUtil.downloadPlugins(configFile);
        return true;
    }

    @Parameters(separators = "=", commandDescription = "Configure and debug plugins")
    private static class CommandPlugin {
        @Parameter(names = "--help", description = "Prints help for plugin command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "List currently activated file provision plugins")
    private static class CommandPluginList {
        @Parameter(names = "--help", description = "Prints help for list command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Download default file provisioning plugins")
    private static class CommandPluginDownload {
        @Parameter(names = "--help", description = "Prints help for download command", help = true)
        private boolean help = false;
    }

}
