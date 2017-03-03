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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import io.dockstore.client.Bridge;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.parser.WdlParser;

/**
 * This defines the set of operations that is needed to interact with a particular
 * source code repository.
 *
 * @author dyuen
 */
public abstract class SourceCodeRepoInterface {

    public static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoInterface.class);

    /**
     * If this interface is pointed at a specific repository, grab a
     * file from a specific branch/tag
     *
     * @param fileName  the name of the file (full path) to retrieve
     * @param reference the tag/branch to get the file from
     * @return content of the file
     */
    public abstract String readFile(String fileName, String reference);

    /**
     * Get the email for the current user
     *
     * @return email for the logged in user
     */
    public abstract String getOrganizationEmail();

    /**
     * Copies some of the attributes of the source workflow to the target workflow
     *
     * @param sourceWorkflow
     * @param targetWorkflow
     */
    protected void copyWorkflow(Workflow sourceWorkflow, Workflow targetWorkflow) {
        targetWorkflow.setPath(sourceWorkflow.getPath());
        targetWorkflow.setIsPublished(sourceWorkflow.getIsPublished());
        targetWorkflow.setWorkflowName(sourceWorkflow.getWorkflowName());
        targetWorkflow.setAuthor(sourceWorkflow.getAuthor());
        targetWorkflow.setEmail(sourceWorkflow.getEmail());
        targetWorkflow.setDescription(sourceWorkflow.getDescription());
        targetWorkflow.setLastModified(sourceWorkflow.getLastModified());
        targetWorkflow.setOrganization(sourceWorkflow.getOrganization());
        targetWorkflow.setRepository(sourceWorkflow.getRepository());
        targetWorkflow.setGitUrl(sourceWorkflow.getGitUrl());
        targetWorkflow.setDescriptorType(sourceWorkflow.getDescriptorType());
        targetWorkflow.setDefaultVersion(sourceWorkflow.getDefaultVersion());
    }

    /**
     * Parses the cwl content to get the author, email, and description, then updates entry.
     *
     * @param entry   an entry to be updated
     * @param content a cwl document
     * @return the updated entry
     */
    protected Entry parseCWLContent(Entry entry, String content) {
        // parse the collab.cwl file to get important metadata
        if (content != null && !content.isEmpty()) {
            try {
                YamlReader reader = new YamlReader(content);
                Object object = reader.read();
                Map map = (Map)object;

                String description = (String)map.get("description");
                // changed for CWL 1.0
                if (map.containsKey("doc")) {
                    description = (String)map.get("doc");
                }
                if (description != null) {
                    entry.setDescription(description);
                } else {
                    LOG.info("Description not found!");
                }

                map = (Map)map.get("dct:creator");
                if (map != null) {
                    String author = (String)map.get("foaf:name");
                    entry.setAuthor(author);
                    String email = (String)map.get("foaf:mbox");
                    if (!Strings.isNullOrEmpty(email)) {
                        entry.setEmail(email.replaceFirst("^mailto:", ""));
                    }
                } else {
                    LOG.info("Creator not found!");
                }

                LOG.info("Repository has Dockstore.cwl");
            } catch (YamlException ex) {
                LOG.info("CWL file is malformed " + ex.getCause().toString());
                throw new CustomWebApplicationException("Could not parse yaml: " + ex.getCause().toString(), HttpStatus.SC_BAD_REQUEST);
            }
        }
        return entry;
    }

    /**
     * Default implementation that parses WDL content from an entry?
     *
     * @param entry   the source for the wdl content
     * @param content the actual wdl content
     * @return the tool that was given
     */
    Entry parseWDLContent(Entry entry, String content) {
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
            } else {
                LOG.info("Repository has Dockstore.wdl");
            }
        } catch (WdlParser.SyntaxError syntaxError) {
            LOG.info("Invalid WDL file.");
        }

        return entry;
    }

    /**
     * Get a map of git url to an id that can uniquely identify a repository
     *
     * @return giturl -> repositoryid
     */
    public abstract Map<String, String> getWorkflowGitUrl2RepositoryId();

    List<String> getWdlImports(File workflowFile) {
        Bridge bridge = new Bridge();
        return bridge.getImportFiles(workflowFile);
    }

    /**
     * Given the content of a file, determines if it is a valid WDL workflow
     *
     * @param content
     * @return true if valid WDL workflow, false otherwise
     */
    public Boolean checkValidWDLWorkflow(String content) {
        //        final NamespaceWithWorkflow nameSpaceWithWorkflow = NamespaceWithWorkflow.load(content);
        //        if (nameSpaceWithWorkflow != null) {
        //            return true;
        //        }
        //
        //        return false;
        // For now as long as a file exists, it is a valid WDL
        return true;
    }

    /**
     * Given the content of a file, determines if it is a valid CWL workflow
     *
     * @param content
     * @return true if valid CWL workflow, false otherwise
     */
    public boolean checkValidCWLWorkflow(String content) {
        if (content.contains("class: Workflow")) {
            return true;
        }

        return false;
    }

    /**
     * Set up workflow with basic attributes from git repository
     *
     * @param repositoryId
     * @return workflow with some attributes set
     */
    public abstract Workflow initializeWorkflow(String repositoryId);

    /**
     * Finds all of the workflow versions for a given workflow and store them and their corresponding source files
     *
     * @param repositoryId
     * @param workflow
     * @param existingWorkflow
     * @param existingDefaults
     * @return workflow with associated workflow versions
     */
    public abstract Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults);

    /**
     * Creates or updates a workflow based on the situation. Will grab workflow versions and more metadata if workflow is FULL
     *
     * @param repositoryId
     * @param existingWorkflow
     * @return workflow
     */
    public Workflow getWorkflow(String repositoryId, Optional<Workflow> existingWorkflow) {
        // Initialize workflow
        Workflow workflow = initializeWorkflow(repositoryId);

        // Determine if workflow should be returned as a STUB or FULL
        if (!existingWorkflow.isPresent()) {
            // when there is no existing workflow at all, just return a stub workflow. Also set descriptor type to default cwl.
            workflow.setDescriptorType(AbstractEntryClient.Type.CWL.toString());
            return workflow;
        }
        if (existingWorkflow.get().getMode() == WorkflowMode.STUB) {
            // when there is an existing stub workflow, just return the new stub as well
            return workflow;
        }

        // If this point has been reached, then the workflow will be a FULL workflow (and not a STUB)
        workflow.setMode(WorkflowMode.FULL);

        // if it exists, extract paths from the previous workflow entry
        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();
        if (existingWorkflow.isPresent()) {
            // Copy over existing workflow versions
            existingWorkflow.get().getWorkflowVersions()
                    .forEach(existingVersion -> existingDefaults.put(existingVersion.getReference(), existingVersion));

            // Copy workflow information from source (existingWorkflow) to target (workflow)
            copyWorkflow(existingWorkflow.get(), workflow);
        }

        // Create branches and associated source files
        setupWorkflowVersions(repositoryId, workflow, existingWorkflow, existingDefaults);

        // Get metadata for workflow and update workflow with it
        if (workflow.getDescriptorType().equals(AbstractEntryClient.Type.CWL.toString())) {
            updateEntryMetadata(workflow, AbstractEntryClient.Type.CWL);
        } else {
            updateEntryMetadata(workflow, AbstractEntryClient.Type.WDL);
        }

        return workflow;
    }

    /**
     * Update an entry with the contents of the descriptor file from a source code repo
     *
     * @param entry@Override
     * @param type
     * @return
     */
    public Entry updateEntryMetadata(Entry entry, AbstractEntryClient.Type type) {
        // Determine which branch to use
        String repositoryId = getRepositoryId(entry);

        if (repositoryId == null) {
            LOG.info("Could not find repository information.");
            return entry;
        }

        String branch = getMainBranch(entry, repositoryId);

        if (branch == null) {
            LOG.info(repositoryId + " : Error getting the main branch.");
            return entry;
        }

        // Determine the file path of the descriptor
        String filePath = null;

        // If entry is a tool
        if (entry.getClass().equals(Tool.class)) {
            // If no tags exist on quay
            if (((Tool)entry).getVersions().size() == 0) {
                return entry;
            }

            // Find filepath to parse
            for (Tag tag : ((Tool)entry).getVersions()) {
                if (tag.getReference() != null && tag.getReference().equals(branch)) {
                    if (type == AbstractEntryClient.Type.CWL) {
                        filePath = tag.getCwlPath();
                    } else {
                        filePath = tag.getWdlPath();
                    }
                }
            }
        }

        // If entry is a workflow
        if (entry.getClass().equals(Workflow.class)) {
            // Find filepath to parse
            for (WorkflowVersion workflowVersion : ((Workflow)entry).getVersions()) {
                if (workflowVersion.getReference().equals(branch)) {
                    filePath = workflowVersion.getWorkflowPath();
                }
            }
        }

        if (Strings.isNullOrEmpty(filePath)) {
            LOG.info(repositoryId + " : No descriptor found for " + branch + ".");
            return entry;
        }

        // Why is this needed?
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }

        // Get file contents
        // Does this need to be an API call? can't we just use the files we have in the database?
        String content = getFileContents(filePath, branch, repositoryId);

        if (content == null) {
            LOG.info(repositoryId + " : Error getting descriptor for " + branch + " with path " + filePath);
            return entry;
        }

        // Parse file content and update
        if (type == AbstractEntryClient.Type.CWL) {
            entry = parseCWLContent(entry, content);
        }
        if (type == AbstractEntryClient.Type.WDL) {
            entry = parseWDLContent(entry, content);
        }

        return entry;
    }

    /**
     * Get the repository Id of an entry to be used for API calls
     *
     * @param entry
     * @return repository id of an entry
     */
    public abstract String getRepositoryId(Entry entry);

    /**
     * Returns the branch of interest used to determine tool and workflow metadata
     *
     * @param entry
     * @param repositoryId
     * @return Branch of interest
     */
    public abstract String getMainBranch(Entry entry, String repositoryId);

    /**
     * Returns the content of a given file from a specific git repository and branch
     *
     * @param filePath
     * @param branch
     * @param repositoryId
     * @return content of a file from git host
     */
    public abstract String getFileContents(String filePath, String branch, String repositoryId);

    /**
     * Initializes workflow version for given branch
     *
     * @param branch
     * @param existingWorkflow
     * @param existingDefaults
     * @return workflow version
     */
    public WorkflowVersion initializeWorkflowVersion(String branch, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults) {
        WorkflowVersion version = new WorkflowVersion();
        version.setName(branch);
        version.setReference(branch);
        version.setValid(false);

        // Determine workflow version from previous

        String calculatedPath;
        String testJsonPath;

        // Set to false if new version
        if (existingDefaults.get(branch) == null) {
            version.setDirtyBit(false);
            calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();
        } else {
            // existing version
            if (existingDefaults.get(branch).isDirtyBit()) {
                calculatedPath = existingDefaults.get(branch).getWorkflowPath();
            } else {
                calculatedPath = existingWorkflow.get().getDefaultWorkflowPath();
            }
            version.setDirtyBit(existingDefaults.get(branch).isDirtyBit());
        }

        version.setWorkflowPath(calculatedPath);

        return version;
    }

    /**
     * Determine descriptor type from file path
     *
     * @param path
     * @return descriptor file type
     */
    public SourceFile.FileType getFileType(String path) {
        String calculatedExtension = FilenameUtils.getExtension(path);
        if ("cwl".equalsIgnoreCase(calculatedExtension) || "yml".equalsIgnoreCase(calculatedExtension) || "yaml"
                .equalsIgnoreCase(calculatedExtension)) {
            return SourceFile.FileType.DOCKSTORE_CWL;
        } else if ("wdl".equalsIgnoreCase(calculatedExtension)) {
            return SourceFile.FileType.DOCKSTORE_WDL;
        } else {
            throw new CustomWebApplicationException("Invalid file type for import", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Resolves imports for a sourcefile, associates with version
     *
     * @param sourceFile
     * @param workflow
     * @param identifiedType
     * @param version
     * @return workflow version
     */
    public WorkflowVersion combineVersionAndSourcefile(SourceFile sourceFile, Workflow workflow, SourceFile.FileType identifiedType,
            WorkflowVersion version, Map<String, WorkflowVersion> existingDefaults) {
        Set<SourceFile> sourceFileSet = new HashSet<>();

        // try to use the FileImporter to re-use code for handling imports
        if (sourceFile.getContent() != null) {
            FileImporter importer = new FileImporter(this);
            final Map<String, SourceFile> stringSourceFileMap = importer
                    .resolveImports(sourceFile.getContent(), workflow, identifiedType, version);
            sourceFileSet.addAll(stringSourceFileMap.values());
        }

        // Look for test parameter files if existing workflow
        if (existingDefaults.get(version.getName()) != null) {
            WorkflowVersion existingVersion = existingDefaults.get(version.getName());
            SourceFile.FileType workflowDescriptorType =
                    (workflow.getDescriptorType().toLowerCase().equals("cwl")) ? SourceFile.FileType.CWL_TEST_JSON
                            : SourceFile.FileType.WDL_TEST_JSON;

            List<SourceFile> testParameterFiles = existingVersion.getSourceFiles().stream()
                    .filter((SourceFile u) -> u.getType() == workflowDescriptorType).collect(Collectors.toList());

            FileImporter importer = new FileImporter(this);
            for (SourceFile testJson : testParameterFiles) {
                String fileResponse = importer.readGitRepositoryFile(workflowDescriptorType, existingVersion, testJson.getPath());
                if (fileResponse != null) {
                    SourceFile dockstoreFile = new SourceFile();
                    dockstoreFile.setType(workflowDescriptorType);
                    dockstoreFile.setContent(fileResponse);
                    dockstoreFile.setPath(testJson.getPath());
                    sourceFileSet.add(dockstoreFile);
                }
            }
        }

        // If source file is found and valid then add it
        if (sourceFile.getContent() != null) {
            version.getSourceFiles().add(sourceFile);
        }

        // The version is valid if source files are found
        if (version.getSourceFiles().size() > 0) {
            version.setValid(true);
        }

        // add extra source files here (dependencies from "main" descriptor)
        if (sourceFileSet.size() > 0) {
            version.getSourceFiles().addAll(sourceFileSet);
        }

        return version;
    }
}
