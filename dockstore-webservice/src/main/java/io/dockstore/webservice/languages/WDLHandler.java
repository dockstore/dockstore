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
package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.common.WdlBridge;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will eventually handle support for understanding WDL
 */
public class WDLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(WDLHandler.class);
    public static final String WDL_SYNTAX_ERROR = "There is a syntax error or your WDL version is greater than draft-2. Please check the WDL file.";
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+\"(\\S+)\"");

    @Override
    public Entry parseWorkflowContent(Entry entry, String filepath, String content, Set<SourceFile> sourceFiles) {
        // Use Broad WDL parser to grab data
        // Todo: Currently just checks validity of file.  In the future pull data such as author from the WDL file
        WdlBridge wdlBridge = new WdlBridge();
        Map<String, String> secondaryFiles = new HashMap<>();
        sourceFiles.stream().forEach(file -> {
            secondaryFiles.put(file.getAbsolutePath(), file.getContent());
        });
        wdlBridge.setSecondaryFiles((HashMap<String, String>)secondaryFiles);

        try {
            List<Map<String, String>> metadata = wdlBridge.getMetadata(filepath);
            Set<String> authors = new HashSet<>();
            Set<String> emails = new HashSet<>();
            final String[] mainDescription = { null };

            metadata.stream().forEach(metaBlock -> {
                String author = metaBlock.get("author");
                String[] callAuthors = author != null ? author.split(",") : null;
                if (callAuthors != null) {
                    for (String callAuthor : callAuthors) {
                        authors.add(callAuthor.trim());
                    }
                }

                String email = metaBlock.get("email");
                String[] callEmails = email != null ? email.split(",") : null;
                if (callEmails != null) {
                    for (String callEmail : callEmails) {
                        emails.add(callEmail.trim());
                    }
                }

                String description = metaBlock.get("description");
                if (description != null && !description.isEmpty() && !description.isBlank()) {
                    mainDescription[0] = description;
                }
            });

            if (!authors.isEmpty()) {
                entry.setAuthor(Joiner.on(", ").join(authors));
            }
            if (!emails.isEmpty()) {
                entry.setEmail(Joiner.on(", ").join(emails));
            }
            if (!Strings.isNullOrEmpty(mainDescription[0])) {
                entry.setDescription(mainDescription[0]);
            }
        } catch (wdl.draft3.parser.WdlParser.SyntaxError ex) {
            LOG.error("Unable to parse WDL file " + filepath);
            clearMetadata(entry);
            return entry;
        }
        return entry;
    }

    private void clearMetadata(Entry entry) {
        entry.setAuthor(null);
        entry.setEmail(null);
        entry.setDescription(WDL_SYNTAX_ERROR);
    }

    /**
     * A common helper method for validating tool and workflow sets
     * @param sourcefiles Set of sourcefiles to validate
     * @param primaryDescriptorFilePath Path of primary descriptor
     * @param type workflow or tool
     * @return
     */
    public VersionTypeValidation validateEntrySet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath, String type) {
        File tempMainDescriptor = null;
        String mainDescriptor = null;

        List<DescriptorLanguage.FileType> fileTypes = new ArrayList<>(Arrays.asList(DescriptorLanguage.FileType.DOCKSTORE_WDL));
        Set<SourceFile> filteredSourceFiles = filterSourcefiles(sourcefiles, fileTypes);

        Map<String, String> validationMessageObject = new HashMap<>();

        if (filteredSourceFiles.size() > 0) {
            try {
                Optional<SourceFile> primaryDescriptor = filteredSourceFiles.stream()
                        .filter(sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath)).findFirst();

                if (primaryDescriptor.isPresent()) {
                    if (primaryDescriptor.get().getContent() == null || primaryDescriptor.get().getContent().trim().replaceAll("\n", "").isEmpty()) {
                        validationMessageObject.put(primaryDescriptorFilePath, "The primary descriptor '" + primaryDescriptorFilePath + "' has no content. Please make it a valid WDL document if you want to save.");
                        return new VersionTypeValidation(false, validationMessageObject);
                    }
                    mainDescriptor = primaryDescriptor.get().getContent();
                } else {
                    validationMessageObject.put(primaryDescriptorFilePath, "The primary descriptor '" + primaryDescriptorFilePath + "' could not be found.");
                    return new VersionTypeValidation(false, validationMessageObject);
                }

                Map<String, String> secondaryDescContent = new HashMap<>();
                for (SourceFile sourceFile : filteredSourceFiles) {
                    if (!Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath) && sourceFile.getContent() != null) {
                        if (sourceFile.getContent().trim().replaceAll("\n", "").isEmpty()) {
                            if (Objects.equals(sourceFile.getType(), DescriptorLanguage.FileType.DOCKSTORE_WDL)) {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it a valid WDL document.");
                            } else if (Objects.equals(sourceFile.getType(), DescriptorLanguage.FileType.WDL_TEST_JSON)) {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it a valid WDL JSON/YAML file.");
                            } else {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it valid.");
                            }
                            return new VersionTypeValidation(false, validationMessageObject);
                        }
                        secondaryDescContent.put(sourceFile.getPath(), sourceFile.getContent());
                    }
                }
                tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
                Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);
                String content = FileUtils.readFileToString(tempMainDescriptor, StandardCharsets.UTF_8);
                checkForRecursiveHTTPImports(content, new HashSet<>());

                WdlBridge wdlBridge = new WdlBridge();
                wdlBridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);

                if (Objects.equals(type, "tool")) {
                    wdlBridge.validateTool(tempMainDescriptor.getAbsolutePath());
                } else {
                    wdlBridge.validateWorkflow(tempMainDescriptor.getAbsolutePath());
                }
            } catch (wdl.draft3.parser.WdlParser.SyntaxError | IllegalArgumentException e) {
                validationMessageObject.put(primaryDescriptorFilePath, e.getMessage());
                return new VersionTypeValidation(false, validationMessageObject);
            } catch (CustomWebApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } finally {
                FileUtils.deleteQuietly(tempMainDescriptor);
            }
        } else {
            validationMessageObject.put(primaryDescriptorFilePath, "Primary WDL descriptor is not present.");
            return new VersionTypeValidation(false, validationMessageObject);
        }
        return new VersionTypeValidation(true, null);
    }

    public void checkForRecursiveHTTPImports(String content, Set<String> currentFileImports) throws IOException {
        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');

        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (match.startsWith("http://") || match.startsWith("https://")) { // Don't resolve URLs
                    if (currentFileImports.contains(match)) {
                        throw new CustomWebApplicationException("Error parsing workflow. You may have a recursive import.",
                                HttpStatus.SC_BAD_REQUEST);
                    } else {
                        currentFileImports.add(match);
                        URL url = new URL(match);
                        try (InputStream is = url.openStream()) {
                            BoundedInputStream boundedInputStream = new BoundedInputStream(is, FileUtils.ONE_MB);
                            String fileContents = IOUtils.toString(boundedInputStream, StandardCharsets.UTF_8);
                            checkForRecursiveHTTPImports(fileContents, currentFileImports);
                        }
                    }
                    currentFileImports.add(match);
                }
            }
        }
    }

    @Override
    public VersionTypeValidation validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return validateEntrySet(sourcefiles, primaryDescriptorFilePath, "workflow");
    }

    @Override
    public VersionTypeValidation validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return validateEntrySet(sourcefiles, primaryDescriptorFilePath, "tool");
    }

    @Override
    public VersionTypeValidation validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return checkValidJsonAndYamlFiles(sourceFiles, DescriptorLanguage.FileType.WDL_TEST_JSON);
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
            SourceCodeRepoInterface sourceCodeRepoInterface, String filepath) {
        return processImports(repositoryId, content, version, sourceCodeRepoInterface, new HashMap<>(), filepath);
    }

    private Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
            SourceCodeRepoInterface sourceCodeRepoInterface, Map<String, SourceFile> imports, String currentFilePath) {
        DescriptorLanguage.FileType fileType = DescriptorLanguage.FileType.DOCKSTORE_WDL;

        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');
        Set<String> currentFileImports = new HashSet<>();

        for (String line : lines) {
            Matcher m = IMPORT_PATTERN.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (!match.startsWith("http://") && !match.startsWith("https://")) { // Don't resolve URLs
                    currentFileImports.add(match.replaceFirst("file://", "")); // remove file:// from path
                }
            }
        }

        for (String importPath : currentFileImports) {
            if (!imports.containsKey(importPath)) {
                SourceFile importFile = new SourceFile();
                String absoluteImportPath = convertRelativePathToAbsolutePath(currentFilePath, importPath);

                final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(repositoryId, fileType, version, absoluteImportPath);
                if (fileResponse == null) {
                    SourceCodeRepoInterface.LOG.error("Could not read: " + absoluteImportPath);
                    continue;
                }
                importFile.setContent(fileResponse);
                importFile.setPath(importPath);
                importFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
                importFile.setAbsolutePath(absoluteImportPath);
                imports.put(importFile.getPath(), importFile);
                imports.putAll(processImports(repositoryId, importFile.getContent(), version, sourceCodeRepoInterface, imports, absoluteImportPath));
            }
        }
        return imports;
    }

    /**
     * This method will get the content for tool tab with descriptor type = WDL
     * It will then call another method to transform the content into JSON string and return
     *
     * @param mainDescName         the name of the main descriptor
     * @param mainDescriptor       the content of the main descriptor
     * @param secondaryDescContent the content of the secondary descriptors in a map, looks like file paths -> content
     * @param type                 tools or DAG
     * @param dao                  used to retrieve information on tools
     * @return either a list of tools or a json map
     */
    @Override
    public String getContent(String mainDescName, String mainDescriptor, Map<String, String> secondaryDescContent,
            LanguageHandlerInterface.Type type, ToolDAO dao) {
        // Initialize general variables
        String callType = "call"; // This may change later (ex. tool, workflow)
        String toolType = "tool";
        // Initialize data structures for DAG
        Map<String, ToolInfo> toolInfoMap;
        Map<String, String> namespaceToPath;
        File tempMainDescriptor = null;
        // Write main descriptor to file
        // The use of temporary files is not needed here and might cause new problems
        try {
            tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
            Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);

            WdlBridge wdlBridge = new WdlBridge();
            wdlBridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);

            // Iterate over each call, grab docker containers
            Map<String, String> callsToDockerMap = wdlBridge.getCallsToDockerMap(tempMainDescriptor.getAbsolutePath());

            // Iterate over each call, determine dependencies
            Map<String, List<String>> callsToDependencies = wdlBridge.getCallsToDependencies(tempMainDescriptor.getAbsolutePath());
            toolInfoMap = mapConverterToToolInfo(callsToDockerMap, callsToDependencies);
            // Get import files
            namespaceToPath = wdlBridge.getImportMap(tempMainDescriptor.getAbsolutePath());
        } catch (IOException | wdl.draft3.parser.WdlParser.SyntaxError e) {
            throw new CustomWebApplicationException("could not process wdl into DAG: " + e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            FileUtils.deleteQuietly(tempMainDescriptor);
        }
        return convertMapsToContent(mainDescName, type, dao, callType, toolType, toolInfoMap, namespaceToPath);
    }

    /**
     * For existing code, converts from maps of untyped data to ToolInfo
     * @param callsToDockerMap map from names of tools to Docker containers
     * @param callsToDependencies map from names of tools to names of their parent tools (dependencies)
     * @return
     */
    static Map<String, ToolInfo> mapConverterToToolInfo(Map<String, String> callsToDockerMap, Map<String, List<String>> callsToDependencies) {
        Map<String, ToolInfo> toolInfoMap;
        toolInfoMap = new HashMap<>();
        callsToDockerMap.forEach((toolName, containerName) -> toolInfoMap.compute(toolName, (key, value) -> {
            if (value == null) {
                return new ToolInfo(containerName, new ArrayList<>());
            } else {
                value.dockerContainer = containerName;
                return value;
            }
        }));
        callsToDependencies.forEach((toolName, dependencies) -> toolInfoMap.compute(toolName, (key, value) -> {
            if (value == null) {
                return new ToolInfo(null, new ArrayList<>());
            } else {
                value.toolDependencyList.addAll(dependencies);
                return value;
            }
        }));
        return toolInfoMap;
    }
}
