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
package io.dockstore.provision;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

/**
 * @author dyuen
 */
public class S3Plugin extends Plugin {

    public S3Plugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        System.out.println("S3Plugin.start()");
        // for testing the development mode
        if (RuntimeMode.DEVELOPMENT.equals(wrapper.getRuntimeMode())) {
            System.out.println(StringUtils.upperCase("S3Plugin"));
        }
    }

    @Override
    public void stop() {
        System.out.println("S3Plugin.stop()");
    }

    @Extension
    public static class S3Provision implements ProvisionInterface {

        public boolean prefixHandled(String path) {
            System.err.println("S3Provision check");
            return true;
        }

        public boolean downloadFrom(String sourcePath, Path destination) {
            return true;
        }

        public boolean uploadTo(String destPath, Path sourceFile, String metadata) {
            return true;
        }

    }

}

