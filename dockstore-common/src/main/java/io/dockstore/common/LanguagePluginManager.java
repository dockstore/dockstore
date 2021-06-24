/*
 *    Copyright 2020 OICR
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
import org.pf4j.DefaultPluginManager;

/**
 * Was intially a static class but the cached singleton was causing issues.
 */
public final class LanguagePluginManager {

    private LanguagePluginManager() {
        // block instantiation
    }

    public static synchronized DefaultPluginManager getInstance(File pluginsDirectory) {
        DefaultPluginManager manager = new DefaultPluginManager(pluginsDirectory.toPath());
        manager.loadPlugins();
        manager.startPlugins();
        return manager;
    }
}
