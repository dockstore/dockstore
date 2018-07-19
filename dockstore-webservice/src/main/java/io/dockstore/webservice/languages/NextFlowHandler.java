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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Lists;
import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import groovyjarjarantlr.RecognitionException;
import groovyjarjarantlr.TokenStreamException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.antlr.GroovySourceAST;
import org.codehaus.groovy.antlr.parser.GroovyLexer;
import org.codehaus.groovy.antlr.parser.GroovyRecognizer;
import org.codehaus.groovy.antlr.parser.GroovyTokenTypes;

/**
 * This class will eventually handle support for NextFlow
 */
public class NextFlowHandler implements LanguageHandlerInterface {

    @Override
    public Entry parseWorkflowContent(Entry entry, String content, Set<SourceFile> sourceFiles) {
        // this is where we can look for things like NextFlow config files or maybe a future Dockstore.yml
        ConfigObject parse = getConfigObject(content);
        ConfigObject manifest = (ConfigObject)parse.get("manifest");
        if (manifest.containsKey("description")) {
            entry.setDescription((String)manifest.get("description"));
        }
        if (manifest.containsKey("author")) {
            entry.setAuthor((String)manifest.get("author"));
        }

        return entry;
    }

    @Override
    public boolean isValidWorkflow(String content) {
        return content.contains("manifest");
    }

    @Override
    public Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface) {
        ConfigObject parse = getConfigObject(content);
        Map<String, SourceFile> imports = new HashMap<>();

        // add the NextFlow scripts
        ConfigObject manifest = (ConfigObject)parse.get("manifest");
        String mainScriptPath = "main.nf";
        if (manifest != null && manifest.containsKey("mainScript")) {
            mainScriptPath = (String)manifest.get("mainScript");
        }
        Optional<SourceFile> sourceFile = sourceCodeRepoInterface.readFile(repositoryId, version, SourceFile.FileType.NEXTFLOW, mainScriptPath);
        if (sourceFile.isPresent()) {
            imports.put(mainScriptPath, sourceFile.get());
        }
        // source files in /lib seem to be automatically added to the script classpath
        // binaries are also there and will need to be ignored
        List<String> strings = sourceCodeRepoInterface.listFiles(repositoryId, "/", version.getReference());
        handleNextFlowImports(repositoryId, version, sourceCodeRepoInterface, imports, strings, "lib");
        handleNextFlowImports(repositoryId, version, sourceCodeRepoInterface, imports, strings, "bin");
        return imports;
    }

    private void handleNextFlowImports(String repositoryId, Version version, SourceCodeRepoInterface sourceCodeRepoInterface,
        Map<String, SourceFile> imports, List<String> strings, String lib) {
        if (strings.contains(lib)) {
            List<String> libraries = sourceCodeRepoInterface.listFiles(repositoryId, lib, version.getReference());
            for (String library : libraries) {
                Optional<SourceFile> librarySourceFile = sourceCodeRepoInterface
                    .readFile(repositoryId, version, SourceFile.FileType.NEXTFLOW, FilenameUtils.concat(lib, library));
                librarySourceFile.ifPresent(sourceFile -> imports.put(lib + "/" + library, sourceFile));
            }
        }
    }

    private ConfigObject getConfigObject(String content) {
        ConfigSlurper slurper = new ConfigSlurper();
        //TODO: replace with NextFlow parser when licensing issues are dealt with
        // this sucks, but we need to ignore includeConfig lines
        String cleanedContent = content.replaceAll("(?m)^[ \t]*includeConfig.*", "");
        return slurper.parse(cleanedContent);
    }

    /**
     * Grabs the path in the AST to the desired child node
     *
     * @param ast
     * @param keyword
     * @param groovyTokenType
     * @return
     */
    private List<GroovySourceAST> extractTargetFromAST(GroovySourceAST ast, String keyword, int groovyTokenType) {
        if (ast == null) {
            return null;
        }
        if (ast.getType() == groovyTokenType && ast.getText().equals(keyword)) {
            return Lists.newArrayList(ast);
        }

        int numChildren = ast.getNumberOfChildren();
        for (int i = 0; i < numChildren; i++) {
            GroovySourceAST groovySourceAST = ast.childAt(i);
            List<GroovySourceAST> target = extractTargetFromAST(groovySourceAST, keyword, groovyTokenType);
            if (target != null) {
                target.add(ast);
                return target;
            }
        }
        return null;
    }

    /**
     * Returns the first AST found with some keyword
     * @param ast
     * @param keyword
     * @return
     */
    private GroovySourceAST getFirstAstWithKeyword(GroovySourceAST ast, String keyword) {
        // Base case
        if (ast == null) {
            return null;
        }

        if (Objects.equals(ast.getText(), keyword)) {
            return ast;
        }

        int numChildren = ast.getNumberOfChildren();
        GroovySourceAST subtree = null;

        for (int i = 0; i < numChildren; i++) {
            GroovySourceAST groovySourceAST = ast.childAt(i);
            subtree = getFirstAstWithKeyword(groovySourceAST, keyword);
            if (subtree != null) {
                break;
            }
        }

        if (subtree == null && ast.getNextSibling() != null) {
            subtree = getFirstAstWithKeyword((GroovySourceAST)ast.getNextSibling(), keyword);
        }
        return subtree;
    }

    /**
     * Returns the first AST found with a child of some keyword
     * @param ast
     * @return
     */
    private GroovySourceAST getAstWhereChildHasSomeKeyword(GroovySourceAST ast, String keyword) {
        // Base case
        if (ast == null) {
            return null;
        }

        if (ast.getFirstChild() != null && Objects.equals(ast.getFirstChild().getText(), keyword)) {
            return ast;
        }

        int numChildren = ast.getNumberOfChildren();
        GroovySourceAST subtree = null;

        for (int i = 0; i < numChildren; i++) {
            GroovySourceAST groovySourceAST = ast.childAt(i);
            subtree = getAstWhereChildHasSomeKeyword(groovySourceAST, keyword);
            if (subtree != null) {
                break;
            }
        }

        if (subtree == null && ast.getNextSibling() != null) {
            subtree = getAstWhereChildHasSomeKeyword((GroovySourceAST)ast.getNextSibling(), keyword);
        }
        return subtree;
    }

    /**
     * Given an AST for an EXPR will return the text name
     * @param ast
     * @return
     */
    private String getInputFileForEXPR(GroovySourceAST ast) {
        if (ast == null) {
            return null;
        } else {
            return ast.getFirstChild().getFirstChild().getNextSibling().getFirstChild().getText();
        }
    }

    private String getProcessValue(GroovySourceAST ast) {
        return ast.getNextSibling().getFirstChild().getFirstChild().getText();
    }

    private List<String> getListOfIO(GroovySourceAST ast) {
        List<String> inputs = new ArrayList<>();
        // get first child pair
        GroovySourceAST firstEXPR = getFirstAstWithKeyword(ast, "EXPR");

        // add to array
        inputs.add(getInputFileForEXPR(firstEXPR));

        // recursively grab children
        if (!(ast.getNextSibling() != null && ast.getNextSibling().getFirstChild() != null && Objects.equals(ast.getNextSibling().getFirstChild().getText(), "output"))) {
            if (ast != null && ast.getNextSibling() != null) {
                inputs.addAll(getListOfIO((GroovySourceAST) ast.getNextSibling()));
            }
        }
        return inputs;
    }

    /**
     * Gets a list of all subtrees with text keyword
     * @param ast
     * @param keyword
     * @return
     */
    private List<GroovySourceAST> getSubtreesOfKeyword(GroovySourceAST ast, String keyword) {
        List<GroovySourceAST> subtrees = new ArrayList<>();

        if (ast == null) {
            return null;
        }

        if (Objects.equals(ast.getText(), keyword)) {
            subtrees.add(ast);
            return subtrees;
        }

        int numChildren = ast.getNumberOfChildren();

        for (int i = 0; i < numChildren; i++) {
            GroovySourceAST groovySourceAST = ast.childAt(i);
            subtrees.addAll(getSubtreesOfKeyword(groovySourceAST, keyword));
        }

        if (ast.getNextSibling() != null) {
            subtrees.addAll(getSubtreesOfKeyword((GroovySourceAST)ast.getNextSibling(), keyword));
        }

        return subtrees;
    }


    /**
     * Returns a list of input dependencies
     * @param ast
     * @return
     */
    private List<String> getInputDependencyList(GroovySourceAST ast) {
        GroovySourceAST inputAST = getAstWhereChildHasSomeKeyword(ast, "input");
        List<String> results = getListOfIO(inputAST);
        return results;
    }

    private List<String> getOutputDependencyList(GroovySourceAST ast) {
        GroovySourceAST outputAst = getAstWhereChildHasSomeKeyword(ast, "output");
        outputAst.setNextSibling(null);
        List<String> results = getListOfIO(outputAst);
        return results;
    }

    @Override
    public String getContent(String mainDescName, String mainDescriptor, Map<String, String> secondaryDescContent, Type type, ToolDAO dao) {
        String callType = "call"; // This may change later (ex. tool, workflow)
        String toolType = "tool";

        // Write main descriptor to file
        // The use of temporary files is not needed here and might cause new problems
        // Iterate over each call, grab docker containers

        // nextflow uses the main script from the manifest as the main descriptor
        // add the NextFlow scripts
        ConfigObject parse = getConfigObject(mainDescriptor);
        ConfigObject manifest = (ConfigObject)parse.get("manifest");
        String mainScriptPath = "main.nf";
        if (manifest != null && manifest.containsKey("mainScript")) {
            mainScriptPath = (String)manifest.get("mainScript");
        }
        mainDescriptor = secondaryDescContent.get(mainScriptPath);

        Map<String, String> callToDockerMap = this.getCallsToDockerMap(mainDescriptor);
        // Iterate over each call, determine dependencies
        // Mapping of stepId -> array of dependencies for the step
        Map<String, List<String>> callToDependencies = this.getCallsToDependencies(mainDescriptor);
        // Get import files
        Map<String, String> namespaceToPath = this.getImportMap(mainDescriptor);
        Map<String, ToolInfo> toolInfoMap = WDLHandler.mapConverterToToolInfo(callToDockerMap, callToDependencies);
        return convertMapsToContent(mainScriptPath, type, dao, callType, toolType, toolInfoMap, namespaceToPath);
    }

    private Map<String, String> getImportMap(String mainDescriptor) {
        //TODO: deal with secondary files properly? (for DAG and tools display)
        return new HashMap<>();
    }

    /**
     * Returns map from names of processes to their dependencies (processes that had to come before)
     *
     * @param mainDescriptor
     * @return
     */
    private Map<String, List<String>> getCallsToDependencies(String mainDescriptor) {
        //TODO: create proper dependency arrays, for now just list processes sequentially
        Map<String, List<String>> map = new HashMap<>();
        try (InputStream stream = IOUtils.toInputStream(mainDescriptor, StandardCharsets.UTF_8)) {
            GroovyRecognizer make = GroovyRecognizer.make(new GroovyLexer(stream));
            make.compilationUnit();

            GroovySourceAST ast = (GroovySourceAST)make.getAST();

            List<GroovySourceAST> processList = getSubtreesOfKeyword(ast, "process");
            Map<String, List<String>> processNameToInputChannels = new HashMap<>();
            Map<String, List<String>> processNameToOutputChannels = new HashMap<>();

            for (GroovySourceAST processAST : processList) {
                String processName = getProcessValue(processAST);

                // Get a list of all channels that the process depends on
                List<String> inputs = getInputDependencyList(processAST);
                processNameToInputChannels.put(processName, inputs);

                // Get a list of all channels that the process writes to
                List<String> outputs = getOutputDependencyList(processAST);
                processNameToOutputChannels.put(processName, outputs);
            }

            // For each process
            for (String processName : processNameToInputChannels.keySet()) {
                List<String> dependencies = new ArrayList<>();

                // For each input dependency
                for (String channelRead : processNameToInputChannels.get(processName)) {
                    // Find if in another process' output dependency
                    for (String dependentProcessName : processNameToOutputChannels.keySet()) {
                        for (String channelWrite : processNameToOutputChannels.get(dependentProcessName)) {
                            if (Objects.equals(channelRead, channelWrite)) {
                                dependencies.add(dependentProcessName);
                                break;
                            }
                        }
                    }
                }
                map.put(processName, dependencies);
            }
        } catch (IOException | TokenStreamException | RecognitionException e) {
            LOG.warn("could not parse", e);
        }
        return map;
    }

    private Map<String, String> getCallsToDockerMap(String mainDescriptor) {
        Map<String, String> map = new HashMap<>();
        try (InputStream stream = IOUtils.toInputStream(mainDescriptor, StandardCharsets.UTF_8)) {
            GroovyRecognizer make = GroovyRecognizer.make(new GroovyLexer(stream));
            make.compilationUnit();
            GroovySourceAST ast = (GroovySourceAST)make.getAST();
            while (ast != null) {
                List<GroovySourceAST> target = extractTargetFromAST(ast, "container", GroovyTokenTypes.IDENT);
                if (target != null) {
                    String processName = getProcessName(target.get(target.size() - 1));
                    String containerName = target.get(0).getNextSibling().getFirstChild().getText();
                    map.put(processName, containerName);
                    LOG.debug("found container: " + containerName + " in process " + processName);
                }
                ast = (GroovySourceAST)ast.getNextSibling();
            }
        } catch (IOException | TokenStreamException | RecognitionException e) {
            LOG.warn("could not parse", e);
        }
        return map;
    }

    /**
     * Gets name of process, this really really sucks, but we can write a test with this and find a better way
     *
     * @param ast
     * @return
     */
    private String getProcessName(GroovySourceAST ast) {
        return ast.getFirstChild().getFirstChild().getNextSibling().getFirstChild().getFirstChild().getText();
    }
}
