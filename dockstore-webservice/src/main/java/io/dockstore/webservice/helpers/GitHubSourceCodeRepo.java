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

import com.google.common.base.Optional;

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
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.NamespaceWithWorkflow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);
    private final String gitUsername;
    private final ContentsService cService;
    private final RepositoryService service;
    private final OrganizationService oService;
    private final String gitRepository;

    // TODO: should be made protected in favour of factory
    public GitHubSourceCodeRepo(String gitUsername, String githubTokenContent, String gitRepository) {

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(githubTokenContent);

        RepositoryService service = new RepositoryService(githubClient);
        ContentsService cService = new ContentsService(githubClient);
        OrganizationService oService = new OrganizationService(githubClient);

        this.service = service;
        this.cService = cService;
        this.oService = oService;
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
    }

    @Override
    public String readFile(String fileName, String reference) {
        checkNotNull(fileName, "The fileName given is null.");
        try {
            Repository repo = service.getRepository(gitUsername, gitRepository); // may need to pass owner from git url, as this may differ from the git username
            List<RepositoryContents> contents;
            try {
                contents = cService.getContents(repo, fileName, reference);
            } catch (Exception e) {
                contents = cService.getContents(repo, fileName.toLowerCase(), reference);
            }

            if (!(contents == null || contents.isEmpty())) {
                return extractGitHubContents(contents);
            } else {
                return null;
            }

        } catch (RequestException e){
            if (e.getStatus() == HttpStatus.SC_UNAUTHORIZED){
                // we have bad credentials which should not be ignored
                throw new CustomWebApplicationException("Error reading from "+gitRepository+", please re-create your git token", HttpStatus.SC_BAD_REQUEST);
            }
            return null;
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readFile" + e.getMessage());
        }
        return null;
    }

    @Override
    public Entry findDescriptor(Entry entry, AbstractEntryClient.Type type) {
        Repository repository = null;
        String repositoryId;


        if (gitRepository == null) {
            if (entry.getClass().equals(Tool.class)) {
                // Parse git url for repo
                Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
                Matcher m = p.matcher(entry.getGitUrl());

                if (!m.find()) {
                    LOG.error(gitUsername + ": Repo: {} could not be retrieved", entry.getGitUrl());
                    repositoryId = null;
                } else {
                    repositoryId = m.group(2);
                }
            } else {
                repositoryId = ((Workflow) entry).getRepository();
            }
        } else {
            repositoryId = gitRepository;
        }


        if (repositoryId != null) {
            try {
                repository = service.getRepository(gitUsername, repositoryId);
            } catch (IOException e) {
                LOG.error(gitUsername + ": Repo: {} could not be retrieved", entry.getGitUrl());
            }
        }
        if (repository == null) {
            if (entry.getClass().equals(Tool.class)) {
                LOG.info(gitUsername + ": Github repository not found for {}", ((Tool) entry).getPath());
            } else {
                LOG.info(gitUsername + ": Github repository not found for {}", ((Workflow) entry).getPath());
            }
        } else {
            LOG.info(gitUsername + ": Github found for: {}", repository.getName());
            try {
                // Determine the default branch on Github
                String mainBranch = repository.getDefaultBranch();

                // Determine which branch to use for tool info
                String branchToUse;
                if (entry.getDefaultVersion() == null) {
                    branchToUse = mainBranch;
                } else {
                    branchToUse = entry.getDefaultVersion();
                }

                // Get file name of interest
                String fileName = "";

                // If tools
                if (entry.getClass().equals(Tool.class)) {
                    // If no tags exist on quay
                    if (((Tool)entry).getVersions().size() == 0) {
                        LOG.info(gitUsername + ": Repo: {} has no tags", repository.getName());
                        return entry;
                    }
                    for (Tag tag : ((Tool)entry).getVersions()) {
                        if (tag.getReference() != null && tag.getReference().equals(branchToUse)) {
                            if (type == AbstractEntryClient.Type.CWL) {
                                fileName = tag.getCwlPath();
                            } else {
                                fileName = tag.getWdlPath();
                            }
                        }
                    }
                }

                // If workflow
                if (entry.getClass().equals(Workflow.class)) {
                    for (WorkflowVersion workflowVersion : ((Workflow) entry).getVersions()) {
                        if (workflowVersion.getReference().equals(branchToUse)) {
                            fileName = workflowVersion.getWorkflowPath();
                        }
                    }
                }

                if (fileName.startsWith("/")) {
                    fileName = fileName.substring(1);
                }

                // Get content of file
                if (fileName != "") {
                    List<RepositoryContents> contents = cService.getContents(repository, fileName, branchToUse);

                    // Parse content
                    if (!(contents == null || contents.isEmpty())) {
                        String content = extractGitHubContents(contents);

                        // Add for new descriptor types
                        // Grab important metadata from CWL file (expects file to have .cwl extension)
                        if (type == AbstractEntryClient.Type.CWL) {
                            entry = parseCWLContent(entry, content);
                        }
                        if (type == AbstractEntryClient.Type.WDL) {
                            entry = parseWDLContent(entry, content);
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.info(gitUsername + ": Repo: {} has no descriptor file ", repository.getName());
            }
        }
        return entry;
    }

    @Override
    public String getOrganizationEmail() {
        User organization;
        try {
            // TODO: only works if the gitUsername is an actual organization on github
            // ie, it does not work if it is just a user
            organization = oService.getOrganization(gitUsername);
        } catch (IOException ex) {
            LOG.info(gitUsername + ": Cannot find Organization {}", gitUsername);
            return "";
        }

        return organization.getEmail();

    }

    @Override public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitURl = new HashMap<>();
        try {
            final List<Repository> repositories = service.getRepositories();
            for(Repository repo : repositories){
                reposByGitURl.put(repo.getSshUrl(), repo.generateId());
            }
            return reposByGitURl;
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getWorkflowGitUrl2RepositoryId workflows {}", gitUsername);
            return null;
        }
    }

    @Override
    public Workflow getNewWorkflow(String repositoryId, Optional<Workflow> existingWorkflow) {
        //TODO: need to add pass-through when paths are custom
        RepositoryId id = RepositoryId.createFromId(repositoryId);
        try {
            final Repository repository = service.getRepository(id);
            LOG.info(gitUsername + ": Looking at repo: " + repository.getSshUrl());
            Workflow workflow = new Workflow();
            workflow.setOrganization(repository.getOwner().getLogin());
            workflow.setRepository(repository.getName());
            workflow.setGitUrl(repository.getSshUrl());
            workflow.setLastUpdated(new Date());
            // make sure path is constructed

            if (!existingWorkflow.isPresent()){
                // when there is no existing workflow at all, just return a stub workflow. Also set descriptor type to default cwl.
                workflow.setDescriptorType("cwl");
                return workflow;
            }
            if (existingWorkflow.get().getMode() == WorkflowMode.STUB){
                // when there is an existing stub workflow, just return the new stub as well
                return workflow;
            }
            workflow.setMode(WorkflowMode.FULL);

            // if it exists, extract paths from the previous workflow entry
            Map<String, String> existingDefaults = new HashMap<>();
            if (existingWorkflow.isPresent()){
                existingWorkflow.get().getWorkflowVersions().forEach(existingVersion -> existingDefaults.put(existingVersion.getReference(), existingVersion.getWorkflowPath()));
                copyWorkflow(existingWorkflow.get(), workflow);
            }

            // when getting a full workflow, look for versions and check each version for valid workflows
            List<String> references = new ArrayList<>();
            service.getBranches(id).forEach(branch -> references.add(branch.getName()));
            service.getTags(id).forEach(tag -> references.add(tag.getName()));

            for (String ref : references) {
                LOG.info(gitUsername + ": Looking at reference: " + ref);
                WorkflowVersion version = new WorkflowVersion();
                version.setName(ref);
                version.setReference(ref);
                version.setValid(false);

                // determine workflow version from previous
                String calculatedPath = existingDefaults.getOrDefault(ref, existingWorkflow.get().getDefaultWorkflowPath());
                version.setWorkflowPath(calculatedPath);
                Set<SourceFile> sourceFileSet = new HashSet<>();
                //TODO: is there a case-insensitive endsWith?
                String calculatedExtension = FilenameUtils.getExtension(calculatedPath);

                if (calculatedExtension.equalsIgnoreCase("cwl") || calculatedExtension.equalsIgnoreCase("yml") || calculatedExtension.equalsIgnoreCase("yaml")) {
                    // look for workflow file
                    try {
                        final List<RepositoryContents> cwlContents = cService.getContents(id, calculatedPath, ref);
                        if (cwlContents != null && cwlContents.size() > 0) {
                            String content = extractGitHubContents(cwlContents);
                            if (content.contains("class: Workflow")) {
                                // if we have a valid workflow document
                                SourceFile file = new SourceFile();
                                file.setType(SourceFile.FileType.DOCKSTORE_CWL);
                                file.setContent(content);
                                file.setPath(calculatedPath);
                                version.getSourceFiles().add(file);

                                // try to use the FileImporter to re-use code for handling imports
                                FileImporter importer = new FileImporter(this);
                                final Map<String, SourceFile> stringSourceFileMap = importer
                                        .resolveImports(content, workflow, SourceFile.FileType.DOCKSTORE_CWL, version);
                                sourceFileSet.addAll(stringSourceFileMap.values());
                            }
                        }

                    } catch (IOException ex) {
                        LOG.info(gitUsername + ": Error getting contents of file.");
                    } catch (Exception ex) {
                        LOG.info(gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid CWL workflow");
                    }
                } else {
                    try {
                        final List<RepositoryContents> wdlContents = cService.getContents(id, calculatedPath, ref);
                        if (wdlContents != null && wdlContents.size() > 0) {
                            String content = extractGitHubContents(wdlContents);

                            final NamespaceWithWorkflow nameSpaceWithWorkflow = NamespaceWithWorkflow.load(content);
                            if (nameSpaceWithWorkflow != null) {
                                // if we have a valid workflow document
                                SourceFile file = new SourceFile();
                                file.setType(SourceFile.FileType.DOCKSTORE_WDL);
                                file.setContent(content);
                                file.setPath(calculatedPath);
                                version.getSourceFiles().add(file);

                                // try to use the FileImporter to re-use code for handling imports
                                FileImporter importer = new FileImporter(this);
                                final Map<String, SourceFile> stringSourceFileMap = importer
                                        .resolveImports(content, workflow, SourceFile.FileType.DOCKSTORE_WDL, version);
                                sourceFileSet.addAll(stringSourceFileMap.values());
                            }
                        }
                    } catch (Exception ex) {
                        LOG.info(gitUsername + ": " + calculatedPath + " on " + ref + " was not valid WDL workflow");
                    }
                }

                if (version.getSourceFiles().size() > 0) {
                    version.setValid(true);
                }

                // add extra source files here
                if (sourceFileSet.size() > 0) {
                    version.getSourceFiles().addAll(sourceFileSet);
                }

                workflow.addWorkflowVersion(version);
            }

            // Get information about default version
            if (workflow.getDescriptorType().equals("cwl")) {
                findDescriptor(workflow, AbstractEntryClient.Type.CWL);
            } else {
                findDescriptor(workflow, AbstractEntryClient.Type.WDL);
            }

            return workflow;
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getNewWorkflow {}");
            return null;
        }
    }

    private String extractGitHubContents(List<RepositoryContents> cwlContents) {
        String encoded = cwlContents.get(0).getContent().replace("\n", "");
        byte[] decode = Base64.getDecoder().decode(encoded);
        return new String(decode, StandardCharsets.UTF_8);
    }

}
