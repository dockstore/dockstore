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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     */
    void readFile(Version tag, Collection<SourceFile> files, SourceFile.FileType fileType, String path) {
        SourceFile sourceFile = this.readFile(tag, fileType, path);
        if (sourceFile != null) {
            files.add(sourceFile);
        }
    }

    /**
     * Read a file from the importer and add it into files
     * @param tag the version of source control we want to read from
     * @param fileType the type of file
     */
    public SourceFile readFile(Version tag, SourceFile.FileType fileType, String path) {
        String fileResponse = this.readGitRepositoryFile(fileType, tag, path);
        if (fileResponse != null) {
            SourceFile dockstoreFile = new SourceFile();
            dockstoreFile.setType(fileType);
            dockstoreFile.setContent(fileResponse);
            dockstoreFile.setPath(path);
            return dockstoreFile;
        }
        return null;
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
     * Get a map of git url to an id that can uniquely identify a repository
     *
     * @return giturl -> repositoryid
     */
    public abstract Map<String, String> getWorkflowGitUrl2RepositoryId();

    /**
     * Checks to see if a particular source code repository is properly setup for issues like token scope
     */
    public abstract boolean checkSourceCodeValidity();


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
        updateEntryMetadata(workflow, workflow.determineWorkflowType());
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
        Set<SourceFile> sourceFiles = null;

        // If entry is a tool
        if (entry.getClass().equals(Tool.class)) {
            // If no tags exist on quay
            if (((Tool)entry).getVersions().size() == 0) {
                return entry;
            }

            // Find filepath to parse
            for (Tag tag : ((Tool)entry).getVersions()) {
                if (tag.getReference() != null && tag.getReference().equals(branch)) {
                    sourceFiles = tag.getSourceFiles();
                    if (type == AbstractEntryClient.Type.CWL) {
                        filePath = tag.getCwlPath();
                    } else if (type == AbstractEntryClient.Type.WDL) {
                        filePath = tag.getWdlPath();
                    } else {
                        throw new UnsupportedOperationException("tool is not a CWL or WDL file");
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
                    sourceFiles = workflowVersion.getSourceFiles();
                }
            }
        }

        if (Strings.isNullOrEmpty(filePath)) {
            LOG.info(repositoryId + " : No descriptor found for " + branch + ".");
            return entry;
        }

        if (sourceFiles == null || sourceFiles.isEmpty()) {
            LOG.info(repositoryId + " : Error getting descriptor for " + branch + " with path " + filePath);
            return entry;
        }

        String firstFileContent;
        String finalFilePath = filePath;
        Optional<SourceFile> first = sourceFiles.stream().filter(file -> file.getPath().equals(finalFilePath)).findFirst();
        if (first.isPresent()) {
            firstFileContent = first.get().getContent();
        } else {
            return entry;
        }

        // Parse file content and update
        LanguageHandlerInterface anInterface = LanguageHandlerFactory.getInterface(type);
        entry = anInterface.parseWorkflowContent(entry, firstFileContent, sourceFiles);
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

        if (sourceFile != null && sourceFile.getContent() != null) {
            final Map<String, SourceFile> stringSourceFileMap = this.resolveImports(sourceFile.getContent(), identifiedType, version);
            sourceFileSet.addAll(stringSourceFileMap.values());
        }

        // Look for test parameter files if existing workflow
        if (existingDefaults.get(version.getName()) != null) {
            WorkflowVersion existingVersion = existingDefaults.get(version.getName());
            SourceFile.FileType workflowDescriptorType = workflow.getTestParameterType();

            List<SourceFile> testParameterFiles = existingVersion.getSourceFiles().stream()
                    .filter((SourceFile u) -> u.getType() == workflowDescriptorType).collect(Collectors.toList());
            testParameterFiles.forEach(file -> this.readFile(existingVersion, sourceFileSet, workflowDescriptorType, file.getPath()));
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
    public String readGitRepositoryFile(SourceFile.FileType fileType, Version version, String specificPath) {

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

    Map<String, SourceFile> resolveImports(String content, SourceFile.FileType fileType, Version version) {
        LanguageHandlerInterface languageInterface = LanguageHandlerFactory.getInterface(fileType);
        return languageInterface.processImports(content, version, this);
    }

    /**
     * The following methods were duplicated code, but are not well designed for this interface
     */

    public abstract SourceFile getSourceFile(String path, String id, String branch, SourceFile.FileType type);

    void createTestParameterFiles(Workflow workflow, String id, String branchName, WorkflowVersion version,
        SourceFile.FileType identifiedType) {
        if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
            // Set Filetype
            SourceFile.FileType testJsonType = null;
            if (identifiedType.equals(SourceFile.FileType.DOCKSTORE_CWL)) {
                testJsonType = SourceFile.FileType.CWL_TEST_JSON;
            } else if (identifiedType.equals(SourceFile.FileType.DOCKSTORE_WDL)) {
                testJsonType = SourceFile.FileType.WDL_TEST_JSON;
            }

            // Check if test parameter file has already been added
            final SourceFile.FileType finalFileType = testJsonType;
            long duplicateCount = version.getSourceFiles().stream().filter((SourceFile v) -> v.getPath().equals(workflow.getDefaultTestParameterFilePath()) && v.getType() == finalFileType).count();
            if (duplicateCount == 0) {
                SourceFile testJsonSourceFile = getSourceFile(workflow.getDefaultTestParameterFilePath(), id, branchName, testJsonType);
                if (testJsonSourceFile != null) {
                    version.getSourceFiles().add(testJsonSourceFile);
                }
            }
        }
    }
}
