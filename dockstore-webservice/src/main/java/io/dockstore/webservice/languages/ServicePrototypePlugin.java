/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.languages;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.Service12;
import io.dockstore.language.RecommendedLanguageInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServicePrototypePlugin implements RecommendedLanguageInterface {
    private static final Logger LOG = LoggerFactory.getLogger(ServicePrototypePlugin.class);

    @Override
    public DescriptorLanguage getDescriptorLanguage() {
        return DescriptorLanguage.SERVICE;
    }

    @Override
    public String launchInstructions(String trsID) {
        return null;
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(String initialPath, String contents,
        Map<String, Pair<String, GenericFileType>> indexedFiles) {

        Map<String, String> validationMessageObject = new HashMap<>();

        boolean isValid = true;
        try {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(contents);
            final Service12 service = dockstoreYaml12.getService();
            if (service == null) {
                validationMessageObject.put(initialPath, "No services are defined.");
                isValid = false;
            } else {
                final List<String> files = service.getFiles();
                if (files == null) {
                    validationMessageObject.put(initialPath, "The key 'files' does not exist.");
                    isValid = false;
                } else {
                    // check that files in .dockstore.yml exist
                    String missingFiles = files.stream().filter(file -> indexedFiles.get("/" + file) == null).map(file -> String.format("'%s'", file))
                        .collect(Collectors.joining(", "));
                    if (!missingFiles.isEmpty()) {
                        validationMessageObject.put(initialPath, String.format("The following file(s) are missing: %s.", missingFiles));
                        isValid = false;
                    }
                }
            }
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            validationMessageObject.put(initialPath, ex.getMessage());
            isValid = false;
        }
        VersionTypeValidation validation = new VersionTypeValidation(isValid, validationMessageObject);
        return validation;
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Map<String, Pair<String, GenericFileType>> indexedFiles) {
        return new VersionTypeValidation(true, Collections.emptyMap());
    }

    @Override
    public Pattern initialPathPattern() {
        return Pattern.compile("/.dockstore/.yml");
    }

    @Override
    public Map<String, Pair<String, GenericFileType>> indexWorkflowFiles(String initialPath, String contents, FileReader reader) {
        Map<String, Pair<String, GenericFileType>> results = new HashMap<>();
        for (String line : contents.split("\\r?\\n")) {
            if (line.startsWith("testFilePath")) {
                final String[] s = line.split(":");
                final String importedFile = reader.readFile(s[1].trim());
                results.put(s[1].trim(), new ImmutablePair<>(importedFile, GenericFileType.TEST_PARAMETER_FILE));
            }
        }
        return results;
    }

    @Override
    public WorkflowMetadata parseWorkflowForMetadata(String initialPath, String contents,
        Map<String, Pair<String, GenericFileType>> indexedFiles) {
        WorkflowMetadata metadata = new WorkflowMetadata();
        try {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(contents);
            // TODO: Temporary; followup with https://github.com/dockstore/dockstore/issues/3356
            final Service12 service = dockstoreYaml12.getService();
            if (service != null) {
                final Service12 service12 = service;
                metadata.setAuthor(service12.getAuthor());
                metadata.setDescription(service12.getDescription());
            }
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            LOG.error("Error parsing service metadata.", ex);
        }
        return metadata;
    }
}
