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
package io.dockstore.provision;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ro.fortsoft.pf4j.ExtensionPoint;

/**
 * Base interface for file provisioning
 */
public interface ProvisionInterface extends ExtensionPoint {

    /**
     * Returns whether a particular file path should be handled by this plugin
     * @return return schemes that this provision interface handles (ex: http, https, ftp, syn, icgc)
     */
    Set<String> schemesHandled();

    /**
     * Handle copying from a particular file source
     * @param sourcePath a string indicating a source for a file, for example
     *                   `https://s3.amazonaws.com/oicr.workflow.bundles/released-bundles/synthetic_bam_for_GNOS_upload/hg19.chr22.5x.normal.bam`
     * @param destination a local file path where the file should be copied to
     * @return true on success
     */
    boolean downloadFrom(String sourcePath, Path destination);

    /**
     * Handle copying to a particular file source
     * @param destPath a string indicating a destination for a file, for example
     *                   `s3://upload.destination/output.bam`
     * @param sourceFile a local file path where the file should be copied from
     * @param metadata optional metadata describing the uploaded file that can be understood by the provisioning plugin
     * @return true on success
     */
    boolean uploadTo(String destPath, Path sourceFile, Optional<String> metadata);

    /**
     * Optional method that can be overridden.
     * Called after uploading a set of files, can be used to prepare metadata (for systems that consume metadata before uploads).
     * @param destPath an array of all upload destinations
     * @param sourceFile an array of all source files
     * @param metadata an array of associated metadata
     * @return
     */
    default boolean prepareFileSet(List<String> destPath, List<Path> sourceFile, List<Optional<String>> metadata) {
        return true;
    }

    /**
     * Optional method that can be overridden.
     * Called after uploading a set of files, can be used to finalize metadata (for systems that consume metadata after uploads).
     * @param destPath an array of all upload destinations
     * @param sourceFile an array of all source files
     * @param metadata an array of associated metadata
     * @return
     */
    default boolean finalizeFileSet(List<String> destPath, List<Path> sourceFile, List<Optional<String>> metadata) {
        return true;
    }

    void setConfiguration(Map<String, String> config);

}
