/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import io.dockstore.common.DockerImageReference;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SnapshotHelper {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotHelper.class);

    private SnapshotHelper() {
    }

    public static void snapshotWorkflow(Workflow workflow, WorkflowVersion workflowVersion, ToolDAO toolDAO) {
        LOG.info("Snapshotting workflow {}, workflow version {}", workflow.getWorkflowPath(), workflowVersion.getName());
        Optional<String> toolsJSONTable = Optional.empty();
        LanguageHandlerInterface lInterface = LanguageHandlerFactory.getInterface(workflow.getFileType());

        // Check if tooltablejson in the DB has the "specifier" key because this key was added later on, so there may be entries in the DB that are missing it.
        // If tooltablejson is missing it, retrieve it again so it has this new key.
        // Don't need to re-retrieve tooltablejson if it's an empty array because it will just return an empty array again (since the workflow has no Docker images).
        String existingToolTableJson = workflowVersion.getToolTableJson();
        if (existingToolTableJson != null && (existingToolTableJson.contains("\"specifier\"") || "[]".equals(existingToolTableJson))) {
            toolsJSONTable = Optional.of(existingToolTableJson);
        } else {
            SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
            if (mainDescriptor != null) {
                // Store tool table json
                toolsJSONTable = lInterface.getContent(workflowVersion.getWorkflowPath(), mainDescriptor.getContent(),
                        extractDescriptorAndSecondaryFiles(workflowVersion), LanguageHandlerInterface.Type.TOOLS, toolDAO);
                toolsJSONTable.ifPresent(workflowVersion::setToolTableJson);
            }
        }

        if (toolsJSONTable.isPresent()) {
            checkAndAddImages(workflowVersion, toolsJSONTable.get(), lInterface);
        }

        // If there is a notebook kernel image, attempt to snapshot it.
        if (workflowVersion.getKernelImagePath() != null) {
            checkAndAddImages(workflowVersion, convertImageToToolsJson(workflowVersion.getKernelImagePath(), lInterface, toolDAO), lInterface);
        }

        // store dag
        if (workflowVersion.getDagJson() == null) {
            SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
            if (mainDescriptor != null) {
                String dagJson = lInterface.getCleanDAG(workflowVersion.getWorkflowPath(), mainDescriptor.getContent(),
                        extractDescriptorAndSecondaryFiles(workflowVersion), LanguageHandlerInterface.Type.DAG, toolDAO);
                workflowVersion.setDagJson(dagJson);
            }
        }

        workflowVersion.setFrozen(true);
    }

    private static void checkAndAddImages(WorkflowVersion version, String toolsJson, LanguageHandlerInterface languageHandler) {
        // Check that a snapshot can occur (all images are referenced by tag or digest).
        languageHandler.checkSnapshotImages(version.getName(), toolsJson);
        // Retrieve the images.
        Set<Image> images = languageHandler.getImagesFromRegistry(toolsJson);
        // Add them to the version.
        version.getImages().addAll(images);
    }

    private static String convertImageToToolsJson(String image, LanguageHandlerInterface languageHandler, ToolDAO toolDAO) {
        LanguageHandlerInterface.DockerSpecifier specifier = LanguageHandlerInterface.determineImageSpecifier(image, DockerImageReference.LITERAL);
        String url = languageHandler.getURLFromEntry(image, toolDAO, specifier);
        LanguageHandlerInterface.DockerInfo info = new LanguageHandlerInterface.DockerInfo("", image, url, specifier);
        return languageHandler.getJSONTableToolContent(Map.of("", info));
    }

    /**
     * This method will find the main descriptor file based on the workflow version passed in the parameter
     *
     * @param workflowVersion workflowVersion with collects sourcefiles
     * @return mainDescriptor
     */
    public static SourceFile getMainDescriptorFile(WorkflowVersion workflowVersion) {

        SourceFile mainDescriptor = null;
        for (SourceFile sourceFile : workflowVersion.getSourceFiles()) {
            if (sourceFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                mainDescriptor = sourceFile;
                break;
            }
        }

        return mainDescriptor;
    }

    /**
     * Populates the return file with the descriptor and secondaryDescContent as a map between file paths and secondary files
     *
     * @param workflowVersion source control version to consider
     * @return secondary file map (string path -> string content)
     */
    public static Set<SourceFile> extractDescriptorAndSecondaryFiles(WorkflowVersion workflowVersion) {
        return workflowVersion.getSourceFiles().stream()
                .filter(sf -> !sf.getPath().equals(workflowVersion.getWorkflowPath()))
                .collect(Collectors.toSet());
    }
}
