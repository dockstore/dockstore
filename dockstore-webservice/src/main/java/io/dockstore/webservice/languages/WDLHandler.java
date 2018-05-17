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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.parser.WdlParser;

/**
 * This class will eventually handle support for understanding WDL
 */
public class WDLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(WDLHandler.class);

    @Override
    public Entry parseWorkflowContent(Entry entry, String content, Set<SourceFile> sourceFiles) {
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
                LOG.info("Error with WDL file.");
                return entry;
            } else {
                LOG.info("Repository has Dockstore.wdl");
            }

            Set<String> authors = new HashSet<>();
            Set<String> emails = new HashSet<>();

            // go rummaging through tasks to look for possible emails and authors
            WdlParser.AstList body = (WdlParser.AstList)ast.getAttribute("body");
            // rummage through tasks, each task should have at most one meta
            body.stream().filter(node -> node instanceof WdlParser.Ast && ((WdlParser.Ast)node).getName().equals("Task")).forEach(node -> {
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
                    });
                }
            });
            entry.setAuthor(Joiner.on(", ").join(authors));
            entry.setEmail(Joiner.on(", ").join(emails));
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.info("Invalid WDL file.");
        }

        return entry;
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

    @Override
    public boolean isValidWorkflow(String content) {
        // TODO: this code looks like it was broken at some point, needs investigation
        //        final NamespaceWithWorkflow nameSpaceWithWorkflow = NamespaceWithWorkflow.load(content);
        //        if (nameSpaceWithWorkflow != null) {
        //            return true;
        //        }
        //
        //        return false;
        // For now as long as a file exists, it is a valid WDL
        return true;
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface) {
        Map<String, SourceFile> imports = new HashMap<>();
        SourceFile.FileType fileType = SourceFile.FileType.DOCKSTORE_WDL;

        // Use matcher to get imports
        String[] lines = StringUtils.split(content, '\n');
        List<String> importPaths = new ArrayList<>();
        Pattern p = Pattern.compile("^import\\s+\"(\\S+)\"");

        for (String line : lines) {
            Matcher m = p.matcher(line);

            while (m.find()) {
                String match = m.group(1);
                if (!match.startsWith("http://") && !match.startsWith("https://")) { // Don't resolve URLs
                    importPaths.add(match.replaceFirst("file://", "")); // remove file:// from path
                }
            }
        }

        for (String importPath : importPaths) {
            SourceFile importFile = new SourceFile();

            final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(repositoryId, fileType, version, importPath);
            if (fileResponse == null) {
                LOG.error("Could not read: " + importPath);
                continue;
            }
            importFile.setContent(fileResponse);
            importFile.setPath(importPath);
            importFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
            imports.put(importFile.getPath(), importFile);
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
        Bridge bridge = new Bridge();
        bridge.setSecondaryFiles((HashMap<String, String>)secondaryDescContent);
        String callType = "call"; // This may change later (ex. tool, workflow)
        String toolType = "tool";

        // Initialize data structures for DAG
        Map<String, List<String>> callToDependencies; // Mapping of stepId -> array of dependencies for the step
        List<Pair<String, String>> nodePairs = new ArrayList<>();
        Map<String, String> callToType = new HashMap<>();

        // Initialize data structures for Tool table
        Map<String, Triple<String, String, String>> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url)

        Map<String, String> callToDockerMap;
        Map<String, String> namespaceToPath;
        File tempMainDescriptor = null;
        // Write main descriptor to file
        // The use of temporary files is not needed here and might cause new problems
        try {
            tempMainDescriptor = File.createTempFile("main", "descriptor", Files.createTempDir());
            Files.asCharSink(tempMainDescriptor, StandardCharsets.UTF_8).write(mainDescriptor);
            // Iterate over each call, grab docker containers
            callToDockerMap = bridge.getCallsToDockerMap(tempMainDescriptor);
            // Iterate over each call, determine dependencies
            callToDependencies = bridge.getCallsToDependencies(tempMainDescriptor);
            // Get import files
            namespaceToPath = bridge.getImportMap(tempMainDescriptor);
        } catch (IOException e) {
            throw new CustomWebApplicationException("could not process wdl into DAG", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            FileUtils.deleteQuietly(tempMainDescriptor);
        }

        // Create nodePairs, callToType, toolID, and toolDocker
        for (Map.Entry<String, String> entry : callToDockerMap.entrySet()) {
            String callId = entry.getKey();
            String docker = entry.getValue();
            nodePairs.add(new MutablePair<>(callId, docker));
            if (Strings.isNullOrEmpty(docker)) {
                callToType.put(callId, callType);
            } else {
                callToType.put(callId, toolType);
            }
            String dockerUrl = null;
            if (!Strings.isNullOrEmpty(docker)) {
                dockerUrl = getURLFromEntry(docker, dao);
            }

            // Determine if call is imported
            String[] callName = callId.replaceFirst("^dockstore_", "").split("\\.");

            if (callName.length > 1) {
                nodeDockerInfo.put(callId, new MutableTriple<>(namespaceToPath.get(callName[0]), docker, dockerUrl));
            } else {
                nodeDockerInfo.put(callId, new MutableTriple<>(mainDescName, docker, dockerUrl));
            }
        }

        // Determine start node edges
        for (Pair<String, String> node : nodePairs) {
            if (callToDependencies.get(node.getLeft()).size() == 0) {
                ArrayList<String> dependencies = new ArrayList<>();
                dependencies.add("UniqueBeginKey");
                callToDependencies.put(node.getLeft(), dependencies);
            }
        }
        nodePairs.add(new MutablePair<>("UniqueBeginKey", ""));

        // Determine end node edges
        Set<String> internalNodes = new HashSet<>(); // Nodes that are not leaf nodes
        Set<String> leafNodes = new HashSet<>(); // Leaf nodes

        for (Map.Entry<String, List<String>> entry : callToDependencies.entrySet()) {
            List<String> dependencies = entry.getValue();
            internalNodes.addAll(dependencies);
            leafNodes.add(entry.getKey());
        }

        // Find leaf nodes by removing internal nodes
        leafNodes.removeAll(internalNodes);

        List<String> endDependencies = new ArrayList<>(leafNodes);

        callToDependencies.put("UniqueEndKey", endDependencies);
        nodePairs.add(new MutablePair<>("UniqueEndKey", ""));

        // Create JSON for DAG/table
        if (type == LanguageHandlerInterface.Type.DAG) {
            return setupJSONDAG(nodePairs, callToDependencies, callToType, nodeDockerInfo);
        } else if (type == LanguageHandlerInterface.Type.TOOLS) {
            return getJSONTableToolContent(nodeDockerInfo);
        }

        return null;
    }

    /**
     * Odd un-used method that seems like it could be useful
     *
     * @param workflowFile
     * @return
     */
    List<String> getWdlImports(File workflowFile) {
        Bridge bridge = new Bridge();
        return bridge.getImportFiles(workflowFile);
    }

}
