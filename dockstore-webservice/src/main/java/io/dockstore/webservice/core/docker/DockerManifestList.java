/*
 *    Copyright 2021 OICR
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

// The manifest list is the “fat manifest” which points to specific image manifests for one or more platforms. It's used by multi-arch images.
// This class is compatible with both the Docker V2 Schema 2 manifest list (https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list)
// and the OCI image index specification (https://github.com/opencontainers/image-spec/blob/main/image-index.md)
public class DockerManifestList {
    private long schemaVersion; // Should be 2 for both Docker V2 schema 2 and OCI

    private DockerPlatformManifest[] manifests;

    public long getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(long schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public DockerPlatformManifest[] getManifests() {
        return manifests;
    }

    public void setManifests(DockerPlatformManifest[] manifests) {
        this.manifests = manifests;
    }
}
