/*
 *    Copyright 2021 OICR and UCSC
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
package io.dockstore.webservice.core.docker;

// The image manifest provides a configuration and a set of layers for a container image
// This class is compatible with both the Docker V2 schema 2 image manifest (https://docs.docker.com/registry/spec/manifest-v2-2/#image-manifest)
// and the OCI image manifest specification (https://github.com/opencontainers/image-spec/blob/main/manifest.md).
public class DockerImageManifest {
    private int schemaVersion; // Should be 2 for both Docker V2 schema 2 and OCI

    private DockerLayer config;

    private DockerLayer[] layers;

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public DockerLayer getConfig() {
        return config;
    }

    public void setConfig(DockerLayer config) {
        this.config = config;
    }

    public DockerLayer[] getLayers() {
        return layers;
    }

    public void setLayers(DockerLayer[] layers) {
        this.layers = layers;
    }
}
