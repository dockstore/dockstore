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
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
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
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import io.dockstore.common.Bridge;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.parser.WdlParser;

/**
 * This class will eventually handle support for understanding WDL
 */
public class WDLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(WDLHandler.class);
    public static final String WDL_SYNTAX_ERROR = "There is a syntax error, please check the WDL file.";
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+\"(\\S+)\"");

    @Override
    public Entry parseWorkflowContent(Entry entry, String filepath, String content, Set<SourceFile> sourceFiles) {
        // Use Broad WDL parser to grab data
        // Todo: Currently just checks validity of file.  In the future pull data such as author from the WDL file
        try {
            WdlParser parser = new WdlParser();
            WdlParser.TokenStream tokens;
            if (entry.getClass().equals(Tool.class)) {
                tokens = new WdlParser.TokenStream(parser.lex(content, FilenameUtils.getName(((Tool)entry).getDefaultWdlPath())));
            } else {
                tokens = new WdlParser.TokenStream(parser.lex(content, FilenameUtils.getName(((Workflow)entry).getDefaultWorkflowPath())));
            }
            WdlParser.Ast ast = (WdlParser.Ast)parser.parse(tokens).toAst();

            if (ast == null) {
                LOG.error(MessageFormat.format("Unable to parse WDL file {0}", filepath));
                clearMetadata(entry);
                return entry;
            } else {
                LOG.info("Repository has Dockstore.wdl");
            }

            Set<String> authors = new HashSet<>();
            Set<String> emails = new HashSet<>();
            final String[] description = { null };

            // go rummaging through tasks to look for possible emails and authors
            WdlParser.AstList body = (WdlParser.AstList)ast.getAttribute("body");
            // rummage through tasks, each task should have at most one meta
            body.stream().filter(node -> node instanceof WdlParser.Ast && (((WdlParser.Ast)node).getName().equals("Task") || ((WdlParser.Ast)node).getName().equals("Workflow"))).forEach(node -> {
                List<WdlParser.Ast> meta = extractTargetFromAST(node, "Meta");
                if (meta != null) {
                    Map<String, WdlParser.AstNode> attributes = meta.get(0).getAttributes();
                    attributes.values().forEach(value -> {
                        String email = extractRuntimeAttributeFromAST(value, "email");
                        if (email != null) {
                            emails.add(email);
                        }
                        String author = extractRuntimeAttributeFromAST(value, "author");
                        if (author != null) {
                            authors.add(author);
                        }
                        String localDesc = extractRuntimeAttributeFromAST(value, "description");
                        if (!Strings.isNullOrEmpty(localDesc)) {
                            description[0] = localDesc;
                        }
                    });
                }
            });
            if (!authors.isEmpty() || entry.getAuthor() == null) {
                entry.setAuthor(Joiner.on(", ").join(authors));
            }
            if (!emails.isEmpty() || entry.getEmail() == null) {
                entry.setEmail(Joiner.on(", ").join(emails));
            }
            if (!Strings.isNullOrEmpty(description[0])) {
                entry.setDescription(description[0]);
            }
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.error(MessageFormat.format("Unable to parse WDL file {0}", filepath), syntaxError);
            clearMetadata(entry);
        }

        return entry;
    }

    private void clearMetadata(Entry entry) {
        entry.setAuthor(null);
        entry.setEmail(null);
        entry.setDescription(WDL_SYNTAX_ERROR);
    }

    private String extractRuntimeAttributeFromAST(WdlParser.AstNode node, String key) {
        if (node == null) {
            return null;
        }
        if (node instanceof WdlParser.AstList) {
            WdlParser.AstList astList = (WdlParser.AstList)node;
            for (WdlParser.AstNode listMember : astList) {
                String result = extractRuntimeAttributeFromAST(listMember, key);
                if (result != null) {
                    return result;
                }
            }
        }
        if (node instanceof WdlParser.Ast) {
            WdlParser.Ast nodeAst = (WdlParser.Ast)node;
            if (nodeAst.getAttribute("key") instanceof WdlParser.Terminal && (((WdlParser.Terminal)nodeAst.getAttribute("key"))
                .getSourceString().equalsIgnoreCase(key))) {
                return ((WdlParser.Terminal)nodeAst.getAttribute("value")).getSourceString();
            }
        }
        return null;
    }

    /**
     * Grabs the path in the AST to the desired child node
     *
     * @param node    a potential parent of the target node
     * @param keyword keyword to look for
     * @return a list of the nodes in the path to the keyword node, terminal first
     */
    private List<WdlParser.Ast> extractTargetFromAST(WdlParser.AstNode node, String keyword) {
        if (node == null) {
            return null;
        }
        if (node instanceof WdlParser.Ast) {
            WdlParser.Ast ast = (WdlParser.Ast)node;
            if (ast.getName().equalsIgnoreCase(keyword)) {
                return Lists.newArrayList(ast);
            }
            Map<String, WdlParser.AstNode> attributes = ast.getAttributes();
            for (java.util.Map.Entry<String, WdlParser.AstNode> entry : attributes.entrySet()) {
                if (entry.getValue() instanceof WdlParser.Ast) {
                    List<WdlParser.Ast> target = extractTargetFromAST(entry.getValue(), keyword);
                    if (target != null) {
                        target.add(ast);
                        return target;
                    }
                } else if (entry.getValue() instanceof WdlParser.AstList) {
                    for (WdlParser.AstNode listNode : ((WdlParser.AstList)entry.getValue())) {
                        List<WdlParser.Ast> target = extractTargetFromAST(listNode, keyword);
                        if (target != null) {
                            target.add(ast);
                            return target;
                        }
                    }
                }
            }

        }
        return null;
    }

    /**
     * A common helper method for validating tool and workflow sets
     * @param sourcefiles Set of sourcefiles to validate
     * @param primaryDescriptorFilePath Path of primary descriptor
     * @param type workflow or tool
     * @return
     */
    public ImmutablePair validateEntrySet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath, String type) {
        File tempMainDescriptor = null;
        String mainDescriptor = null;

        List<SourceFile.FileType> fileTypes = new ArrayList<>(Arrays.asList(SourceFile.FileType.DOCKSTORE_WDL));
        Set<SourceFile> filteredSourceFiles = filterSourcefiles(sourcefiles, fileTypes);

        Map<String, String> validationMessageObject = new HashMap<>();

        if (filteredSourceFiles.size() > 0) {
            try {
                Optional<SourceFile> primaryDescriptor = filteredSourceFiles.stream().filter(sourceFile -> Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath)).findFirst();

                if (primaryDescriptor.isPresent()) {
                    if (primaryDescriptor.get().getContent() == null || primaryDescriptor.get().getContent().trim().replaceAll("\n", "").isEmpty()) {
                        validationMessageObject.put(primaryDescriptorFilePath, "The primary descriptor '" + primaryDescriptorFilePath + "' has no content. Please make it a valid WDL document if you want to save.");
                        return new ImmutablePair<>(false, validationMessageObject);
                    }
                    mainDescriptor = primaryDescriptor.get().getContent();
                } else {
                    validationMessageObject.put(primaryDescriptorFilePath, "The primary descriptor '" + primaryDescriptorFilePath + "' could not be found.");
                    return new ImmutablePair<>(false, validationMessageObject);
                }

                Map<String, String> secondaryDescContent = new HashMap<>();
                for (SourceFile sourceFile : filteredSourceFiles) {
                    if (!Objects.equals(sourceFile.getPath(), primaryDescriptorFilePath) && sourceFile.getContent() != null) {
                        if (sourceFile.getContent().trim().replaceAll("\n", "").isEmpty()) {
                            if (Objects.equals(sourceFile.getType(), SourceFile.FileType.DOCKSTORE_WDL)) {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it a valid WDL document.");
                            } else if (Objects.equals(sourceFile.getType(), SourceFile.FileType.WDL_TEST_JSON)) {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it a valid WDL JSON/YAML file.");
                            } else {
                                validationMessageObject.put(primaryDescriptorFilePath, "File '" + sourceFile.getPath() + "' has no content. Either delete the file or make it valid.");
                            }
                            return new ImmutablePair<>(false, validationMessageObject);
                        }
                        secondaryDescContent.put(sourceFile.getPath(), sourceFile.getContent());
                    }
                }
                tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
                Bridge bridge = new Bridge(tempMainDescriptor.getParent());
                bridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);
                Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);
                if (Objects.equals(type, "tool")) {
                    bridge.isValidTool(tempMainDescriptor);
                } else {
                    bridge.isValidWorkflow(tempMainDescriptor);
                }
            } catch (WdlParser.SyntaxError | IllegalArgumentException e) {
                validationMessageObject.put(primaryDescriptorFilePath, e.getMessage());
                return new ImmutablePair<>(false, validationMessageObject);
            } catch (Exception e) {
                throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } finally {
                FileUtils.deleteQuietly(tempMainDescriptor);
            }
        } else {
            validationMessageObject.put(primaryDescriptorFilePath, "Primary WDL descriptor is not present.");
            return new ImmutablePair<>(false, validationMessageObject);
        }
        return new ImmutablePair<>(true, null);
    }

    @Override
    public ImmutablePair validateWorkflowSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return validateEntrySet(sourcefiles, primaryDescriptorFilePath, "workflow");
    }

    @Override
    public ImmutablePair validateToolSet(Set<SourceFile> sourcefiles, String primaryDescriptorFilePath) {
        return validateEntrySet(sourcefiles, primaryDescriptorFilePath, "tool");
    }

    @Override
    public ImmutablePair validateTestParameterSet(Set<SourceFile> sourceFiles) {
        return checkValidJsonAndYamlFiles(sourceFiles, SourceFile.FileType.WDL_TEST_JSON);
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String filepath) {
        return processImports(repositoryId, content, version, sourceCodeRepoInterface, new HashMap<>(), filepath);
    }

    private Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, Map<String, SourceFile> imports, String currentFilePath) {
        SourceFile.FileType fileType = SourceFile.FileType.DOCKSTORE_WDL;

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
                importFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
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
            Bridge bridge = new Bridge(tempMainDescriptor.getParent());
            bridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);
            Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);

            // Iterate over each call, grab docker containers
            Map<String, String> callsToDockerMap = bridge.getCallsToDockerMap(tempMainDescriptor);
            // Iterate over each call, determine dependencies
            Map<String, List<String>> callsToDependencies = bridge.getCallsToDependencies(tempMainDescriptor);
            toolInfoMap = mapConverterToToolInfo(callsToDockerMap, callsToDependencies);
            // Get import files
            namespaceToPath = bridge.getImportMap(tempMainDescriptor);
        } catch (IOException | WdlParser.SyntaxError e) {
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
