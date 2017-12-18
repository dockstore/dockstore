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

package io.dockstore.webservice.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import io.dockstore.client.Bridge;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.commons.io.FileUtils;
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

    String gitUsername;
    String gitRepository;

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
     * Read a file from the importer and add it into files
     * @param tag the version of source control we want to read from
     * @param files the files collection we want to add to
     * @param fileType the type of file
     * @param sourceFile the sourcefile to read
     */
    void readFile(Version tag, Collection<SourceFile> files, SourceFile.FileType fileType, SourceFile sourceFile) {
        String fileResponse = this.readGitRepositoryFile(fileType, tag, sourceFile.getPath());
        if (fileResponse != null) {
            SourceFile dockstoreFile = new SourceFile();
            dockstoreFile.setType(fileType);
            dockstoreFile.setContent(fileResponse);
            dockstoreFile.setPath(sourceFile.getPath());
            files.add(dockstoreFile);
        }
    }

    /**
     * Get the email for the current user
     *
     * @return email for the logged in user
     */
    public abstract String getOrganizationEmail();

    /**
     * Updates the username and repository used to retrieve files from github
     * Note that this is only called when refreshing multiple workflows at once, because
     * the code does not instantiate them (since they vary)
     * Ex. ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow (breaks up into examples below)
     * @param username ex. ICGC-TCGA-PanCancer
     * @param repository ex. wdl-pcawg-sanger-cgp-workflow
     */
    public void updateUsernameAndRepository(String username, String repository) {
        this.gitUsername = username;
        this.gitRepository = repository;
    }

    /**
     * Parses the cwl content to get the author, email, and description, then updates entry.
     *
     * @param entry   an entry to be updated
     * @param content a cwl document
     * @return the updated entry
     */
    private Entry parseCWLContent(Entry entry, String content) {
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
    private Entry parseWDLContent(Entry entry, String content) {
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

    /**
     * Checks to see if a particular source code repository is properly setup for issues like token scope
     */
    public abstract boolean checkSourceCodeValidity();

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
    Boolean checkValidWDLWorkflow(String content) {
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
    boolean checkValidCWLWorkflow(String content) {
        return content.contains("class: Workflow");
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

        // NextFlow and (future) dockstore.yml workflow can be detected and handled without stubs

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
            existingWorkflow.get().copyWorkflow(workflow);
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
    Entry updateEntryMetadata(Entry entry, AbstractEntryClient.Type type) {
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
    WorkflowVersion initializeWorkflowVersion(String branch, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults) {
        WorkflowVersion version = new WorkflowVersion();
        version.setName(branch);
        version.setReference(branch);
        version.setValid(false);

        // Determine workflow version from previous
        String calculatedPath;

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
    SourceFile.FileType getFileType(String path) {
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
    WorkflowVersion combineVersionAndSourcefile(SourceFile sourceFile, Workflow workflow, SourceFile.FileType identifiedType,
        WorkflowVersion version, Map<String, WorkflowVersion> existingDefaults) {
        Set<SourceFile> sourceFileSet = new HashSet<>();

        // try to use the FileImporter to re-use code for handling imports
        if (sourceFile != null && sourceFile.getContent() != null) {
            final Map<String, SourceFile> stringSourceFileMap = this.resolveImports(sourceFile.getContent(), workflow, identifiedType, version);
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
            testParameterFiles.forEach(file -> this.readFile(existingVersion, sourceFileSet, workflowDescriptorType, file));
        }

        // If source file is found and valid then add it
        if (sourceFile != null && sourceFile.getContent() != null) {
            version.getSourceFiles().add(sourceFile);
        }

        // add extra source files here (dependencies from "main" descriptor)
        if (sourceFileSet.size() > 0) {
            version.getSourceFiles().addAll(sourceFileSet);
        }

        return version;
    }


    /**
     * Look in a source code repo for a particular file
     * @param fileType
     * @param version
     * @param specificPath if specified, look for a specific file, otherwise return the "default" for a fileType
     * @return  a FileResponse instance
     */
    String readGitRepositoryFile(SourceFile.FileType fileType, Version version, String specificPath) {

        final String reference = version.getReference();

        // Do not try to get file if the reference is not available
        if (reference == null) {
            return null;
        }

        String fileName = "";
        if (specificPath != null) {
            String workingDirectory = version.getWorkingDirectory();
            if (specificPath.startsWith("/")) {
                // if we're looking at an absolute path, ignore the working directory
                fileName = specificPath;
            } else if (!workingDirectory.isEmpty() && !"/".equals(workingDirectory)) {
                // if the working directory is different from the root, take it into account
                fileName = workingDirectory + "/" +  specificPath;
            } else {
                fileName = specificPath;
            }
        } else if (version instanceof Tag) {
            Tag tag = (Tag)version;
            // Add for new descriptor types
            if (fileType == SourceFile.FileType.DOCKERFILE) {
                fileName = tag.getDockerfilePath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {
                if (Strings.isNullOrEmpty(tag.getCwlPath())) {
                    return null;
                }
                fileName = tag.getCwlPath();
            } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
                if (Strings.isNullOrEmpty(tag.getWdlPath())) {
                    return null;
                }
                fileName = tag.getWdlPath();
            }
        } else if (version instanceof WorkflowVersion) {
            WorkflowVersion workflowVersion = (WorkflowVersion)version;
            fileName = workflowVersion.getWorkflowPath();
        }

        return this.readFile(fileName, reference);
    }

    Map<String, SourceFile> resolveImports(String content, Entry entry, SourceFile.FileType fileType, Version version) {

        Map<String, SourceFile> imports = new HashMap<>();

        if (fileType == SourceFile.FileType.DOCKSTORE_CWL) {

            YamlReader reader = new YamlReader(content);
            try {
                Map<String, ?> map = reader.read(Map.class);
                handleMap(entry, fileType, version, imports, map);

            } catch (YamlException e) {
                SourceCodeRepoInterface.LOG.error("Could not process content from " + entry.getId() + " as yaml");
            }

            Map<String, SourceFile> recursiveImports = new HashMap<>();
            for (SourceFile file : imports.values()) {
                final Map<String, SourceFile> sourceFiles = resolveImports(file.getContent(), entry, fileType, version);
                recursiveImports.putAll(sourceFiles);
            }
            recursiveImports.putAll(imports);
            return recursiveImports;
        } else if (fileType == SourceFile.FileType.DOCKSTORE_WDL) {
            final File tempDesc;
            try {
                tempDesc = File.createTempFile("temp", ".wdl", Files.createTempDir());
                Files.write(content, tempDesc, StandardCharsets.UTF_8);

                // Use matcher to get imports
                List<String> lines = FileUtils.readLines(tempDesc, StandardCharsets.UTF_8);
                ArrayList<String> importPaths = new ArrayList<>();
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

                    final String fileResponse = readGitRepositoryFile(fileType, version, importPath);
                    if (fileResponse == null) {
                        SourceCodeRepoInterface.LOG.error("Could not read: " + importPath);
                        continue;
                    }
                    importFile.setContent(fileResponse);
                    importFile.setPath(importPath);
                    importFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
                    imports.put(importFile.getPath(), importFile);
                }
            } catch (IOException e) {
                throw new CustomWebApplicationException("Internal server error, out of space",
                    HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE);
            }

            return imports;
        } else {
            throw new CustomWebApplicationException("Invalid file type for import", HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void handleMap(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports,
        Map<String, ?> map) {
        for (Map.Entry<String, ?> e : map.entrySet()) {
            final Object mapValue = e.getValue();
            if (e.getKey().equalsIgnoreCase("$import") || e.getKey().equalsIgnoreCase("$include") || e.getKey().equalsIgnoreCase("import")
                || e.getKey().equalsIgnoreCase("include")) {
                // handle imports and includes
                if (mapValue instanceof String) {
                    handleImport(fileType, version, imports, (String)mapValue);
                }
            } else if (e.getKey().equalsIgnoreCase("run")) {
                // for workflows, bare files may be referenced. See https://github.com/ga4gh/dockstore/issues/208
                //ex:
                //  run: {import: revtool.cwl}
                //  run: revtool.cwl
                if (mapValue instanceof String) {
                    handleImport(fileType, version, imports, (String)mapValue);
                } else if (mapValue instanceof Map) {
                    // this handles the case where an import is used
                    handleMap(entry, fileType, version, imports, (Map)mapValue);
                }
            } else {
                handleMapValue(entry, fileType, version, imports, mapValue);
            }
        }
    }

    private void handleMapValue(Entry entry, SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports,
        Object mapValue) {
        if (mapValue instanceof Map) {
            handleMap(entry, fileType, version, imports, (Map)mapValue);
        } else if (mapValue instanceof List) {
            for (Object listMember : (List)mapValue) {
                handleMapValue(entry, fileType, version, imports, listMember);
            }
        }
    }

    void handleImport(SourceFile.FileType fileType, Version version, Map<String, SourceFile> imports, String mapValue) {
        // create a new source file
        final String fileResponse = readGitRepositoryFile(fileType, version, mapValue);
        if (fileResponse == null) {
            SourceCodeRepoInterface.LOG.error("Could not read: " + mapValue);
            return;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(mapValue);
        imports.put(mapValue, sourceFile);
    }
}
