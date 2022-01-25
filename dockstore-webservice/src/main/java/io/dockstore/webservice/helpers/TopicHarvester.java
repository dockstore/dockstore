package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import java.util.List;

public class TopicHarvester {

    private final GitHubSourceCodeRepo repo;

    public TopicHarvester(GitHubSourceCodeRepo repo) {
        this.repo = repo;
    }

    public TopicHarvester(Token githubToken) {
        this(githubToken != null ? (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(githubToken) : null);
    }

    public TopicHarvester(User user, TokenDAO tokenDAO) {
        this(firstElementOrNull(tokenDAO.findGithubByUserId(user.getId())));
    }

    private static <T> T firstElementOrNull(List<? extends T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public void harvestAndSetTopic(Entry<?, ?> entry) {
        if (repo == null) {
            return;
        }
        String repositoryId = repo.getRepositoryId(entry);
        if (repositoryId == null) {
            return;
        }
        String topic = repo.getTopic(repositoryId);
        entry.setTopicAutomatic(topic);
    }
}
