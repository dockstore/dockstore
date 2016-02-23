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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dockstore.webservice.CustomWebApplicationException;

/**
 * @author dyuen
 */
public class SourceCodeRepoFactory {

    static final Logger LOG = LoggerFactory.getLogger(SourceCodeRepoFactory.class);

    public static SourceCodeRepoInterface createSourceCodeRepo(String gitUrl, HttpClient client, String bitbucketTokenContent,
            String githubTokenContent) {

        Map<String, String> repoUrlMap = parseGitUrl(gitUrl);

        if (repoUrlMap == null) {
            return null;
        }

        String source = repoUrlMap.get("Source");
        String gitUsername = repoUrlMap.get("Username");
        String gitRepository = repoUrlMap.get("Repository");

        SourceCodeRepoInterface repo;
        if ("github.com".equals(source)) {
            repo = new GitHubSourceCodeRepo(gitUsername, githubTokenContent, gitRepository);
        } else if ("bitbucket.org".equals(source)) {
            if (bitbucketTokenContent != null) {
                repo = new BitBucketSourceCodeRepo(gitUsername, client, bitbucketTokenContent, gitRepository);
            } else {
                LOG.info("WARNING: Source is from Bitbucket, but user does not have Bitbucket token!");
                return null;
            }
        } else {
            LOG.info("Do not support: " + source);
            throw new CustomWebApplicationException("Sorry, we do not support " + source + ".", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        return repo;
    }

    /**
     * Parse Git URL to retrieve source, username and repository name.
     *
     * @param url
     * @return a map with keys: Source, Username, Repository
     */
    public static Map<String, String> parseGitUrl(String url) {
        Pattern p = Pattern.compile("git\\@(\\S+):(\\S+)/(\\S+)\\.git");
        Matcher m = p.matcher(url);
        if (!m.find()) {
            LOG.info("Cannot parse url: " + url);
            return null;
        }
        // These correspond to the positions of the pattern matcher
        final int sourceIndex = 1;
        final int usernameIndex = 2;
        final int reponameIndex = 3;

        String source = m.group(sourceIndex);
        String gitUsername = m.group(usernameIndex);
        String gitRepository = m.group(reponameIndex);
         LOG.info("Source: " + source);
         LOG.info("Username: " + gitUsername);
         LOG.info("Repository: " + gitRepository);

        Map<String, String> map = new HashMap<>();
        map.put("Source", source);
        map.put("Username", gitUsername);
        map.put("Repository", gitRepository);

        return map;
    }
}
