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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;

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
    public FileResponse readFile(String fileName, String reference) {
        FileResponse cwl = new FileResponse();
        checkNotNull(fileName, "The fileName given is null.");
        try {
            Repository repo = service.getRepository(gitUsername, gitRepository);
            List<RepositoryContents> contents;
            try {
                contents = cService.getContents(repo, fileName, reference);
            } catch (Exception e) {
                contents = cService.getContents(repo, fileName.toLowerCase(), reference);
            }

            if (!(contents == null || contents.isEmpty())) {
                String encoded = contents.get(0).getContent().replace("\n", "");
                byte[] decode = Base64.getDecoder().decode(encoded);
                String content = new String(decode, StandardCharsets.UTF_8);
                // builder.append(content);
                cwl.setContent(content);
            } else {
                return null;
            }

        } catch (IOException e) {
            // e.printStackTrace();
            LOG.error(e.getMessage());
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
            LOG.error("Repo: {} could not be retrieved", c.getGitUrl());
        }
        if (repository == null) {
            LOG.info("Github repository not found for {}", c.getPath());
        } else {
            LOG.info("Github found for: {}", repository.getName());
            try {
                List<RepositoryContents> contents;
                contents = cService.getContents(repository, fileName);
                if (!(contents == null || contents.isEmpty())) {
                    String encoded = contents.get(0).getContent().replace("\n", "");
                    byte[] decode = Base64.getDecoder().decode(encoded);
                    String content = new String(decode, StandardCharsets.UTF_8);

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
                LOG.info("Repo: {} has no descriptor file ", repository.getName());
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
            LOG.info("Cannot find Organization {}", gitUsername);
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
            LOG.info("Cannot getWorkflowGitUrl2RepositoryId workflows {}", gitUsername);
            return null;
        }
    }

    @Override
    public void updateWorkflow(Workflow workflow) {
        throw new UnsupportedOperationException();
    }

    @Override public Workflow getNewWorkflow(String repositoryId) {
        RepositoryId id = RepositoryId.createFromId(repositoryId);
        try {
            final Repository repository = service.getRepository(id);
            Workflow workflow = new Workflow();
            workflow.setOrganization(repository.getOwner().getLogin());
            workflow.setRepository(repository.getName());
            workflow.setGitUrl(repository.getGitUrl());
            return workflow;
        } catch (IOException e) {
            LOG.info("Cannot getNewWorkflow {}", gitUsername);
            return null;
        }
    }

}
