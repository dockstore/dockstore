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
package io.dockstore.common.yaml;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

/**
 * Abstract base class for services defined in .dockstore.yml
 */
public abstract class AbstractYamlService {
    private String name;
    private String author;
    private String description;
    private List<String> files;
    private Scripts scripts;
    private Map<String, EnvironmentVariable> environment;
    private Map<String, DataSet> data;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(final String author) {
        this.author = author;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(final List<String> files) {
        this.files = files;
    }

    public Scripts getScripts() {
        return scripts;
    }

    public void setScripts(final Scripts scripts) {
        this.scripts = scripts;
    }

    public Map<String, EnvironmentVariable> getEnvironment() {
        return environment;
    }

    public void setEnvironment(final Map<String, EnvironmentVariable> environment) {
        this.environment = environment;
    }

    public Map<String, DataSet> getData() {
        return data;
    }

    public void setData(final Map<String, DataSet> data) {
        this.data = data;
    }

    public static class Scripts {
        private String port;
        private String postprovision;
        private String poststart;
        private String preprovision;
        private String prestart;
        @NotNull
        private String start;
        @NotNull
        private String stop;

        public String getPort() {
            return port;
        }

        public void setPort(final String port) {
            this.port = port;
        }

        public String getPostprovision() {
            return postprovision;
        }

        public void setPostprovision(final String postprovision) {
            this.postprovision = postprovision;
        }

        public String getPoststart() {
            return poststart;
        }

        public void setPoststart(final String poststart) {
            this.poststart = poststart;
        }

        public String getPreprovision() {
            return preprovision;
        }

        public void setPreprovision(final String preprovision) {
            this.preprovision = preprovision;
        }

        public String getPrestart() {
            return prestart;
        }

        public void setPrestart(final String prestart) {
            this.prestart = prestart;
        }

        public String getStart() {
            return start;
        }

        public void setStart(final String start) {
            this.start = start;
        }

        public String getStop() {
            return stop;
        }

        public void setStop(final String stop) {
            this.stop = stop;
        }
    }

    public static class EnvironmentVariable {
        private String defaultValue; // default is a key word
        @NotNull
        private String description;

        public String getDefault() {
            return defaultValue;
        }

        public void setDefault(final String newDefault) {
            this.defaultValue = newDefault;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(final String description) {
            this.description = description;
        }
    }

    public static class DataSet {
        @NotNull
        private String targetDirectory;
        private Map<String, YamlService11.FileDesc> files;

        public String getTargetDirectory() {
            return targetDirectory;
        }

        public void setTargetDirectory(final String targetDirectory) {
            this.targetDirectory = targetDirectory;
        }

        public Map<String, YamlService11.FileDesc> getFiles() {
            return files;
        }

        public void setFiles(final Map<String, YamlService11.FileDesc> files) {
            this.files = files;
        }
    }

    public static class FileDesc {
        @NotNull
        private String description;

        private String targetDirectory;

        public String getDescription() {
            return description;
        }

        public void setDescription(final String description) {
            this.description = description;
        }

        public String getTargetDirectory() {
            return targetDirectory;
        }

        public void setTargetDirectory(final String targetDirectory) {
            this.targetDirectory = targetDirectory;
        }
    }
}
