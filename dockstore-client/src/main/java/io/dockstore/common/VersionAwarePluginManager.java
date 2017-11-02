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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.zafarkhaja.semver.Version;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginDescriptor;
import ro.fortsoft.pf4j.PluginException;
import ro.fortsoft.pf4j.util.AndFileFilter;
import ro.fortsoft.pf4j.util.DirectoryFileFilter;
import ro.fortsoft.pf4j.util.NotFileFilter;

/**
 * This extends the default plugin manager to be version-aware (take the latest version of a plugin)
 */
public class VersionAwarePluginManager extends DefaultPluginManager {

    private static final Logger LOG = LoggerFactory.getLogger(VersionAwarePluginManager.class);
    // maps plugin id -> version, directory path
    private Map<String, Pair<Version, File>> pluginVersionMap = new HashMap<>();

    /**
     * Constructs VersionAwarePluginManager which the given plugins directory.
     *
     * @param pluginsDirectory the directory to search for plugins
     */
    VersionAwarePluginManager(File pluginsDirectory) {
        super(pluginsDirectory);
    }

    void cleanupOldVersions() {
        this.loadPlugins();
        // check for no plugins
        AndFileFilter pluginsFilter = new AndFileFilter(new DirectoryFileFilter());
        pluginsFilter.addFileFilter(new NotFileFilter(createHiddenPluginFilter()));
        File[] directories = pluginsDirectory.listFiles(pluginsFilter);
        if (directories == null) {
            directories = new File[0];
        }
        LOG.debug("Found {} possible plugins: {}", directories.length, directories);
        // load any plugin from plugins directory
        for (File directory : directories) {
            try {
                processPluginDirectory(directory);
            } catch (PluginException e) {
                LOG.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        // wipe out old versions of plugins
        Set<File> fileSet = pluginVersionMap.values().stream().map(Pair::getRight).collect(Collectors.toSet());
        for (File directory : directories) {
            if (!fileSet.contains(directory)) {
                LOG.debug("Deleting old version of plugin in {}", directory.getAbsoluteFile());
                try {
                    FileUtils.deleteDirectory(directory);
                    FileUtils.deleteQuietly(new File(directory.getAbsoluteFile() + ".zip"));
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void processPluginDirectory(File pluginDirectory) throws PluginException {
        // try to load the plugin
        String pluginName = pluginDirectory.getName();
        String pluginPath = "/".concat(pluginName);

        // retrieves the plugin descriptor
        LOG.debug("Find plugin descriptor '{}'", pluginPath);
        PluginDescriptor pluginDescriptor = pluginDescriptorFinder.find(pluginDirectory);

        pluginVersionMap.putIfAbsent(pluginDescriptor.getPluginId(), new ImmutablePair<>(pluginDescriptor.getVersion(), pluginDirectory));

        Pair<Version, File> existingVersion = pluginVersionMap.get(pluginDescriptor.getPluginId());
        if (pluginDescriptor.getVersion().greaterThan(existingVersion.getLeft())) {
            pluginVersionMap.put(pluginDescriptor.getPluginId(), new ImmutablePair<>(pluginDescriptor.getVersion(), pluginDirectory));
        }

    }
}
