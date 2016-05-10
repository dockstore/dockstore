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
import com.google.common.io.Files;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wdl4s.NamespaceWithWorkflow;

import java.io.File;
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
    public FileResponse readFile(String fileName, String reference, String gitUrl) {
        FileResponse cwl = new FileResponse();
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
                String content = extractGitHubContents(contents);
                // builder.append(content);
                cwl.setContent(content);
            } else {
                return null;
            }

        } catch (IOException e) {
            // e.printStackTrace();
            LOG.error(gitUsername + ": " + e.getMessage());
            return null;
        }
        return cwl;
    }

    @Override
    public Tool findDescriptor(Tool c, String fileName) {
        String descriptorType = FilenameUtils.getExtension(fileName);
        Repository repository = null;
        try {
            repository = service.getRepository(gitUsername, gitRepository);
        } catch (IOException e) {
            LOG.error(gitUsername + ": Repo: {} could not be retrieved", c.getGitUrl());
        }
        if (repository == null) {
            LOG.info(gitUsername + ": Github repository not found for {}", c.getPath());
        } else {
            LOG.info(gitUsername + ": Github found for: {}", repository.getName());
            try {
                List<RepositoryContents> contents;
                contents = cService.getContents(repository, fileName);
                if (!(contents == null || contents.isEmpty())) {
                    String content = extractGitHubContents(contents);

                    // Add for new descriptor types
                    // Grab important metadata from CWL file (expects file to have .cwl extension)
                    if (descriptorType.equals("cwl")) {
                        c = parseCWLContent(c, content);
                    }
                    if (descriptorType.equals("wdl")) {
                        c = parseWDLContent(c, content);
                    }
                    
                    // Currently only can pull name of task? or workflow from WDL
                    // Add this later, should call parseWDLContent and use the existing Broad WDL parser
                }
            } catch (IOException ex) {
                LOG.info(gitUsername + ": Repo: {} has no descriptor file ", repository.getName());
            }
        }
        return c;
    }

    @Override
    public String getOrganizationEmail() {
        User organization = null;

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
                reposByGitURl.put(repo.getGitUrl(), repo.generateId());
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
            LOG.info(gitUsername + ": Looking at repo: " + repository.getGitUrl());
            Workflow workflow = new Workflow();
            workflow.setOrganization(repository.getOwner().getLogin());
            workflow.setRepository(repository.getName());
            workflow.setGitUrl(repository.getGitUrl());
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

                // Get relative path of main workflow descriptor to find relative paths
                String[] path = calculatedPath.split("/");
                String basepath = "";
                for (int i = 0; i < path.length - 1; i++) {
                    basepath += path[i] + "/";
                }

                ArrayList<String> importPaths;
                Set<SourceFile> sourceFileSet = new HashSet<>();


                //TODO: is there a case-insensitive endsWith?
                if (calculatedPath.endsWith(".cwl") || calculatedPath.endsWith(".CWL")) {
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
                                final File tempDesc = File.createTempFile("temp", ".cwl", Files.createTempDir());
                                Files.write(content, tempDesc, StandardCharsets.UTF_8);
                                importPaths = getCwlImports(tempDesc);
                                for (String importPath : importPaths) {
                                    LOG.info(gitUsername + ": Grabbing file " + basepath + importPath);
                                    SourceFile importFile = new SourceFile();
                                    importFile.setContent(extractGitHubContents(cService.getContents(id, basepath + importPath, ref)));
                                    importFile.setPath(basepath + importPath);
                                    importFile.setType(SourceFile.FileType.DOCKSTORE_CWL);
                                    sourceFileSet.add(importFile);
                                }
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
                                final File tempDesc = File.createTempFile("temp", ".wdl", Files.createTempDir());
                                Files.write(content, tempDesc, StandardCharsets.UTF_8);
                                importPaths = getWdlImports(tempDesc);
                                for (String importPath : importPaths) {
                                    LOG.info(gitUsername + ": Grabbing file " + importPath);
                                    SourceFile importFile = new SourceFile();
                                    importFile.setContent(extractGitHubContents(cService.getContents(id, basepath + importPath, ref)));
                                    importFile.setPath(basepath + importPath);
                                    importFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
                                    sourceFileSet.add(importFile);
                                }
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
