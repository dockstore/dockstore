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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        SourceFile sourceFile = sourceCodeRepoInterface.readFile(repositoryId, version, SourceFile.FileType.NEXTFLOW, mainScriptPath);
        if (sourceFile != null) {
            imports.put(mainScriptPath, sourceFile);
        }
        // TODO: does NextFlow have imports beyond the main script file linked to from nextflow.config?
        // kinda, source files in /lib seem to be automatically added, binaries are also there and will need to be ignored
        return imports;
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
        //TODO: deal with secondary files properly?
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

            List<GroovySourceAST> lastProcess = null;
            GroovySourceAST ast = (GroovySourceAST)make.getAST();
            while (ast != null) {
                List<GroovySourceAST> target = extractTargetFromAST(ast, "process", GroovyTokenTypes.IDENT);
                ast = (GroovySourceAST)ast.getNextSibling();
                if (target != null) {
                    String processName = getProcessName(target.get(target.size() - 1));
                    LOG.debug("found process: " + processName);
                    if (lastProcess != null) {
                        String lastProcessName = getProcessName(lastProcess.get(lastProcess.size() - 1));
                        map.put(processName, Lists.newArrayList(lastProcessName));
                    } else {
                        map.put(processName, Lists.newArrayList());
                    }
                    lastProcess = target;
                }
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
