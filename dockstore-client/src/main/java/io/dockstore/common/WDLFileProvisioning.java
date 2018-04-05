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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class deals with file provisioning for WDL
 * Created by aduncan on 10/03/16.
 */
public class WDLFileProvisioning {
    private static final Logger LOG = LoggerFactory.getLogger(WDLFileProvisioning.class);

    private final FileProvisioning fileProvisioning;

    public WDLFileProvisioning(String configFile) {
        fileProvisioning = new FileProvisioning(configFile);
    }

    /**
     * Pulls remote files from S3, DCC or HTTP and stores them locally.
     * A map is created to replace the input file entries in the input JSON file, where remote paths will be changed to local paths.
     *
     * @param inputFilesJson    Map of all input files from the input JSON file, key = fully qualified name(fqn), value = type (ex. file)
     * @param originalInputJson Map of input JSON file
     * @return A new mapping of fully qualified name to input file string or list of input file strings
     */
    public Map<String, Object> pullFiles(Map<String, Object> inputFilesJson, Map<String, String> originalInputJson) {
        // Download remote files into specific local locations
        Map<String, Object> fileMap = new HashMap<>();

        System.out.println("Provisioning your input files to your local machine");
        String uniqueHash = UUID.randomUUID().toString();

        List<Pair<String, Path>> inputSet = new ArrayList<>();
        // Go through input file fully qualified names
        for (Map.Entry<String, String> originalInputJsonEntry : originalInputJson.entrySet()) {
            LOG.info(originalInputJsonEntry.getKey());
            // Find matching name value in JSON parameter file
            for (Map.Entry<String, Object> stringObjectEntry : inputFilesJson.entrySet()) {
                // If the entry matches
                if (stringObjectEntry.getKey().equals(originalInputJsonEntry.getKey())) {
                    // Check if File or Array of Files
                    if (stringObjectEntry.getValue() instanceof ArrayList) {
                        // Iterate through object
                        List stringObjectEntryList = (List)stringObjectEntry.getValue();
                        ArrayList<String> updatedPaths = new ArrayList<>();
                        for (Object entry : stringObjectEntryList) {
                            if (entry instanceof String) {
                                updatedPaths.add(doProcessFile(stringObjectEntry.getKey(), entry.toString(), uniqueHash, inputSet)
                                        .get(stringObjectEntry.getKey()).toString());
                            }
                        }

                        fileMap.put(stringObjectEntry.getKey(), updatedPaths);

                    } else if (stringObjectEntry.getValue() instanceof String) {
                        // Just a file
                        Map<String, Object> tempMap;
                        tempMap = doProcessFile(stringObjectEntry.getKey(), stringObjectEntry.getValue().toString(), uniqueHash, inputSet);
                        fileMap.putAll(tempMap);
                    }
                }
            }
        }
        fileProvisioning.provisionInputFiles("", inputSet);

        return fileMap;
    }

    /**
     * Create a mapping of the input files, including newly localized files
     *
     * @param key  Fully Qualified Name
     * @param path Original Path
     * @param inputSet
     * @return Mapping of fully qualified name to new input file string or list of new input file strings
     */
    private Map<String, Object> doProcessFile(String key, String path, String uniqueHash, List<Pair<String, Path>> inputSet) {
        Map<String, Object> jsonEntry = new HashMap<>();

        LOG.info("PATH TO DOWNLOAD FROM: {} FOR {}", path, key);

        // Setup local paths
        String downloadDirPath = "cromwell-input/" + uniqueHash;

        // Check if download dir exists
        File downloadDir = new File(downloadDirPath);
        if (!downloadDir.exists()) {
            Utilities.executeCommand("mkdir -p " + downloadDirPath);
        }

        // Handle provisioning of file
        final Path targetFilePath = Paths.get(downloadDir.getAbsolutePath(), path);
        File originalFile = new File(path);
        System.out.println("Downloading: " + key + " from " + path + " to: " + targetFilePath);
        if (originalFile.isDirectory()) {
            // If directory we will create a copy of it, but not of the content
            Utilities.executeCommand("mkdir -p " + targetFilePath.toString());
        } else {
            inputSet.add(ImmutablePair.of(path, targetFilePath));
            //fileProvisioning.provisionInputFile("", path, targetFilePath);
        }

        jsonEntry.put(key, targetFilePath);
        LOG.info("DOWNLOADED FILE: LOCAL: {} URL: {} => {}", key, path, targetFilePath);
        return jsonEntry;
    }

    /**
     * Creates a new mapping to represent the Input JSON file with updated file paths
     *
     * @param originalInputJson
     * @param newInputJson
     * @return Path to new JSON file
     */
    public String createUpdatedInputsJson(Map<String, Object> originalInputJson, Map<String, Object> newInputJson) {
        JSONObject newJSON = new JSONObject();
        for (Map.Entry<String, Object> entry : originalInputJson.entrySet()) {
            String paramName = entry.getKey();
            boolean isNew = false; // is the entry from the newInputJson mapping?

            // Get value of mapping
            final Object currentParam = entry.getValue();

            // Iterate through new input mapping until you find a matching FQN
            for (Map.Entry<String, Object> newEntry : newInputJson.entrySet()) {
                if (paramName.equals(newEntry.getKey())) {
                    isNew = true;
                    try {
                        newJSON.put(newEntry.getKey(), newEntry.getValue());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            // If not a file, will just add as is
            if (!isNew) {
                try {
                    newJSON.put(paramName, currentParam);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        // Now make a new file
        final Path path = writeJob(newJSON);
        return path.toFile().getAbsolutePath();
    }

    /**
     * Writes a given JSON object to new file jobOutputPath
     *
     * @param newJson JSON object to be saved to file
     */
    private Path writeJob(JSONObject newJson) {
        try {
            final Path tempFile = Files.createTempFile("foo", "json");
            //TODO: investigate, why is this replacement occurring?
            final String replace = newJson.toString().replace("\\", "");
            FileUtils.writeStringToFile(tempFile.toFile(), replace, StandardCharsets.UTF_8);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Could not write job ", e);
        }
    }
}
