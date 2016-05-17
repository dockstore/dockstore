/*
 *    Copyright 2016 OICR
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

package io.dockstore.webservice.helpers;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Optional;
import io.dockstore.client.Bridge;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import wdl4s.parser.WdlParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * This defines the set of operations that is needed to interact with a particular
 * source code repository.
 * @author dyuen
 */
public abstract class SourceCodeRepoInterface {

    public static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoInterface.class);

    /**
     * If this interface is pointed at a specific repository, grab a
     * file from a specific branch/tag
     * @param fileName the name of the file (full path) to retrieve
     * @param reference the tag/branch to get the file from
     * @param gitUrl the git url for the git repository
     * @return a wrapper for the file
     */
    public abstract FileResponse readFile(String fileName, String reference, String gitUrl);

    /**
     * Update a container with the contents of the descriptor file from a source code repo
     * 
     * @param c a container to be updated
     * @return an updated container with fields from the descriptor filled in
     */
    public abstract Tool findDescriptor(Tool c, String fileName);

    /**
     * Get the email for the current user
     * @return email for the logged in user
     */
    public abstract String getOrganizationEmail();

    /**
     *
     * @param sourceWorkflow
     * @param targetWorkflow
         */
    protected void copyWorkflow(Workflow sourceWorkflow, Workflow targetWorkflow) {
        targetWorkflow.setPath(sourceWorkflow.getPath());
        targetWorkflow.setIsPublished(sourceWorkflow.getIsPublished());
        targetWorkflow.setWorkflowName(sourceWorkflow.getWorkflowName());
        targetWorkflow.setAuthor(sourceWorkflow.getAuthor());
        targetWorkflow.setDescription(sourceWorkflow.getDescription());
        targetWorkflow.setLastModified(sourceWorkflow.getLastModified());
        targetWorkflow.setOrganization(sourceWorkflow.getOrganization());
        targetWorkflow.setRepository(sourceWorkflow.getRepository());
        targetWorkflow.setGitUrl(sourceWorkflow.getGitUrl());
        targetWorkflow.setDescriptorType(sourceWorkflow.getDescriptorType());
    }

    /**
     * Parses the cwl content to get the author and description. Updates the tool with the author, description, and hasCollab fields.
     *
     * @param tool a tool to be updated
     * @param content a cwl document
     * @return the updated tool
     */
    protected Tool parseCWLContent(Tool tool, String content) {
        // parse the collab.cwl file to get description and author
        if (content != null && !content.isEmpty()) {
            try {
                YamlReader reader = new YamlReader(content);
                Object object = reader.read();
                Map map = (Map) object;

                String description = (String) map.get("description");
                if (description != null) {
                    tool.setDescription(description);
                } else {
                    LOG.info("Description not found!");
                }

                map = (Map) map.get("dct:creator");
                if (map != null) {
                    String author = (String) map.get("foaf:name");
                    tool.setAuthor(author);
                } else {
                    LOG.info("Creator not found!");
                }

                // tool.setHasCollab(true);
                tool.setValidTrigger(true);
                LOG.info("Repository has Dockstore.cwl");
            } catch (IOException ex) {
                LOG.info("CWL file is malformed");
                ex.printStackTrace();
            }
        }
        return tool;
    }

    /**
     * Default implementation that parses WDL content from a tool?
     * TODO: does this belong here?
     * @param tool the source for the wdl content
     * @param content the actual wdl content
     * @return the tool that was given
     */
    protected Tool parseWDLContent(Tool tool, String content) {
        // Use Broad WDL parser to grab data
        // Todo: Currently just checks validity of file.  In the future pull data such as author from the WDL file
        try {
            String wdlSource = content;
            WdlParser parser = new WdlParser();
            WdlParser.TokenStream tokens = new WdlParser.TokenStream(parser.lex(wdlSource, FilenameUtils.getName(tool.getDefaultWdlPath())));
            WdlParser.Ast ast = (WdlParser.Ast) parser.parse(tokens).toAst();

            if (ast == null) {
                LOG.info("Error with WDL file.");
            } else {
                tool.setValidTrigger(true);
                LOG.info("Repository has Dockstore.wdl");
            }
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.info("Invalid WDL file.");
        }

        return tool;
    }

    /**
     * Get a map of git url to an id that can uniquely identify a repository
     * @return giturl -> repositoryid
     */
    public abstract Map<String, String> getWorkflowGitUrl2RepositoryId();

    /**
     * Given a repositoryid, get a workflow
     * TODO: pass in an existing workflow when we need to override paths
     * @param repositoryId uniquely identify a repo
     * @param existingWorkflow an existing workflow entry, when existingWorkflow is a stub or empty, simply create a new stub entry
     *                         when existingWorkflow is a full workflow, do a full refresh but use the existing workflow paths as a guide
     *                         for where to look for files
     * @return a fully realized workflow
     */
    public abstract Workflow getNewWorkflow(String repositoryId, Optional<Workflow> existingWorkflow);

    public ArrayList<String> getCwlImports(File workflowFile) throws FileNotFoundException {
        ArrayList<String> imports = new ArrayList<>();
        Yaml yaml = new Yaml();
        Map <String, Object> groups = (Map<String, Object>) yaml.load(new FileInputStream(workflowFile));

        for (String group : groups.keySet()) {
            if (group.equals("steps")) {
                ArrayList<Map <String, Object>> steps = (ArrayList<Map <String, Object>>) groups.get(group);
                for (Map <String, Object> step : steps) {
                    Map<String, Object> stepParams = (Map<String, Object>)step.get("run");
                    imports.add(stepParams.get("import").toString());
                }
            }
        }

        return imports;
    }

    public ArrayList<String> getWdlImports(File workflowFile) {
        Bridge bridge = new Bridge();
        ArrayList<String> imports = bridge.getImportFiles(workflowFile);

        return imports;
    }

    public static class FileResponse {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}
