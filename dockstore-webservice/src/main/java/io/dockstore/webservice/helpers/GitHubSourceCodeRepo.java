package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dockstore.webservice.core.Container;

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
    public Container findCWL(Container c) {
        String fileName = c.getDefaultCwlPath();

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

                    c = parseCWLContent(c, content);
                }
            } catch (IOException ex) {
                LOG.info("Repo: {} has no Dockstore.cwl", repository.getName());
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

}
