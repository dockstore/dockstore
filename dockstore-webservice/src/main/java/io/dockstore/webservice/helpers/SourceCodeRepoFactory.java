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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public final class SourceCodeRepoFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoFactory.class);

    private SourceCodeRepoFactory() {
        // hide the constructor for utility classes
    }

    public static SourceCodeRepoInterface createGitHubAppRepo(String token) {
        // The gitUsername doesn't seem to matter
        final GitHubSourceCodeRepo repo = new GitHubSourceCodeRepo("dockstore", token, "JWT");
        repo.checkSourceCodeValidity();
        return repo;
    }

    public static SourceCodeRepoInterface createSourceCodeRepo(Token token) {
        SourceCodeRepoInterface repo;
        if (Objects.equals(token.getTokenSource(), TokenType.GITHUB_COM)) {
            repo = new GitHubSourceCodeRepo(token.getUsername(), token.getContent(), token.getUsername());
        } else if (Objects.equals(token.getTokenSource(), TokenType.BITBUCKET_ORG)) {
            repo = new BitBucketSourceCodeRepo(token.getUsername(), token.getContent());
        } else if (Objects.equals(token.getTokenSource(), TokenType.GITLAB_COM)) {
            repo = new GitLabSourceCodeRepo(token.getUsername(), token.getContent());
        } else {
            LOG.error("We do not currently support: " + token.getTokenSource());
            throw new CustomWebApplicationException("Sorry, we do not support " + token.getTokenSource() + ".",
                HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        repo.checkSourceCodeValidity();
        return repo;
    }

    public static SourceCodeRepoInterface createSourceCodeRepo(String gitUrl, HttpClient client, String bitbucketTokenContent,
            String gitlabTokenContent, Token githubToken) {

        Map<String, String> repoUrlMap = parseGitUrl(gitUrl);

        if (repoUrlMap == null) {
            return null;
        }

        String source = repoUrlMap.get("Source");
        String gitUsername = repoUrlMap.get("Username");

        SourceCodeRepoInterface repo;
        if (SourceControl.GITHUB.toString().equals(source)) {
            repo = new GitHubSourceCodeRepo(gitUsername, githubToken.getContent(), githubToken.getUsername());
        } else if (SourceControl.BITBUCKET.toString().equals(source)) {
            if (bitbucketTokenContent != null) {
                repo = new BitBucketSourceCodeRepo(gitUsername, bitbucketTokenContent);
            } else {
                LOG.info("WARNING: Source is from Bitbucket, but user does not have Bitbucket token!");
                return null;
            }
        } else if (SourceControl.GITLAB.toString().equals(source)) {
            if (gitlabTokenContent != null) {
                repo = new GitLabSourceCodeRepo(gitUsername, gitlabTokenContent);
            } else {
                LOG.info("WARNING: Source is from Gitlab, but user does not have Gitlab token!");
                return null;
            }
        } else {
            LOG.info("Do not support: " + source);
            throw new CustomWebApplicationException("Sorry, we do not support " + source + ".", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        repo.checkSourceCodeValidity();
        return repo;
    }

    /**
     * Gets source code repo corresponding to the Git URL. Only checks if a token is found for that source code repo, no additional checks.
     * @param gitUrl    Git URL to identify which SourceCodeRepo to return
     * @param bitbucketTokenContent The user's Bitbucket token if it exists, null otherwise
     * @param gitlabTokenContent    The user's GitLab token if it exists, null otherwise
     * @param githubToken    The user's GitHub token if it exists, null otherwise
     * @return  a SourceCode repo if a token exists, null otherwise
     */
    public static SourceCodeRepoInterface createSourceCodeRepo(String gitUrl, String bitbucketTokenContent,
            String gitlabTokenContent, Token githubToken) {
        Map<String, String> repoUrlMap = parseGitUrl(gitUrl);
        if (repoUrlMap == null) {
            return null;
        }
        String source = repoUrlMap.get("Source");
        String gitUsername = repoUrlMap.get("Username");
        if (SourceControl.GITHUB.toString().equals(source)) {
            return githubToken != null ? new GitHubSourceCodeRepo(gitUsername, githubToken.getContent(), githubToken.getUsername()) : null;
        } else if (SourceControl.BITBUCKET.toString().equals(source)) {
            return bitbucketTokenContent != null ? new BitBucketSourceCodeRepo(gitUsername, bitbucketTokenContent) : null;
        } else if (SourceControl.GITLAB.toString().equals(source)) {
            return (gitlabTokenContent != null ? new GitLabSourceCodeRepo(gitUsername, gitlabTokenContent) : null);
        } else {
            return null;
        }
    }

    /**
     * Parse Git URL to retrieve source, username and repository name.
     *
     * @param url
     * @return a map with keys: Source, Username, Repository
     */
    public static Map<String, String> parseGitUrl(String url) {
        // format 1 git@github.com:dockstore/dockstore-ui.git
        Pattern p1 = Pattern.compile("git\\@(\\S+):(\\S+)/(\\S+)\\.git");
        Matcher m1 = p1.matcher(url);
        // format 2 git://github.com/denis-yuen/dockstore-whalesay.git (should be avoided)
        Pattern p2 = Pattern.compile("git://(\\S+)/(\\S+)/(\\S+)\\.git");
        Matcher m2 = p2.matcher(url);

        Matcher matcherActual;
        if (m1.find()) {
            matcherActual = m1;
        } else if (m2.find()) {
            matcherActual = m2;
        } else {
            LOG.info("Cannot parse url using any format: " + url);
            return null;
        }

        final int sourceIndex = 1;
        final int usernameIndex = 2;
        final int reponameIndex = 3;
        String source = matcherActual.group(sourceIndex);
        String gitUsername = matcherActual.group(usernameIndex);
        String gitRepository = matcherActual.group(reponameIndex);

        LOG.debug("Source: " + source);
        LOG.debug("Username: " + gitUsername);
        LOG.debug("Repository: " + gitRepository);

        Map<String, String> map = new HashMap<>();
        map.put("Source", source);
        map.put("Username", gitUsername);
        map.put("Repository", gitRepository);
        return map;
    }
}
