package io.dockstore.webservice.helpers;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dyuen
 */
public class SourceCodeRepoFactory {

    static Logger log = LoggerFactory.getLogger(SourceCodeRepoFactory.class);

    public static SourceCodeRepoInterface createSourceCodeRepo(RepositoryService service, ContentsService cService, String gitUrl,
            HttpClient client, String bitbucketTokenContent) {

        Map<String, String> repoUrlMap = parseGitUrl(gitUrl);

        if (repoUrlMap == null) {
            return null;
        }

        String source = repoUrlMap.get("Source");
        String gitUsername = repoUrlMap.get("Username");
        String gitRepository = repoUrlMap.get("Repository");

        SourceCodeRepoInterface repo;
        if (source.equals("github.com")) {
            repo = new GitHubSourceCodeRepo(service, cService, gitUsername, gitRepository);
        } else if (source.equals("bitbucket.org") && bitbucketTokenContent != null) {
            repo = new BitBucketSourceCodeRepo(gitUsername, client, bitbucketTokenContent, gitRepository);
        } else {
            log.info("Do not support: " + source);
            throw new WebApplicationException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        return repo;
    }

    /**
     * Parse Git URL to retrieve source, username and repository name.
     *
     * @param url
     * @return a map with keys: Source, Username, Repository
     */
    static Map<String, String> parseGitUrl(String url) {
        Pattern p = Pattern.compile("git\\@(\\S+):(\\S+)/(\\S+)\\.git");
        Matcher m = p.matcher(url);
        if (!m.find()) {
            log.info("Cannot parse url: " + url);
            return null;
        }

        // These correspond to the positions of the pattern matcher
        final int sourceIndex = 1;
        final int usernameIndex = 2;
        final int reponameIndex = 3;

        String source = m.group(sourceIndex);
        String gitUsername = m.group(usernameIndex);
        String gitRepository = m.group(reponameIndex);
        log.info("Source: " + source);
        log.info("Username: " + gitUsername);
        log.info("Repository: " + gitRepository);

        Map<String, String> map = new HashMap<>();
        map.put("Source", source);
        map.put("Username", gitUsername);
        map.put("Repository", gitRepository);

        return map;
    }
}
