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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.helpers.Helper.RepoList;
import io.dockstore.webservice.resources.ResourceUtilities;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.api.UserApi;
import io.swagger.quay.client.model.UserView;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author dyuen
 */
public class QuayImageRegistry implements ImageRegistryInterface {

    public static final String QUAY_URL = "https://quay.io/api/v1/";

    private static final Logger LOG = LoggerFactory.getLogger(QuayImageRegistry.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Token quayToken;
    private final ApiClient apiClient;

    public QuayImageRegistry(final HttpClient client, final ObjectMapper objectMapper, final Token quayToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.quayToken = quayToken;

        apiClient = Configuration.getDefaultApiClient();
        apiClient.addDefaultHeader("Authorization", "Bearer " + quayToken.getContent());
        // apiClient.setBasePath(QUAY_URL);
    }

    @Override
    public List<Tag> getTags(Tool tool) {
        LOG.info(quayToken.getUsername() + " ======================= Getting tags for: {}================================", tool.getPath());
        final String repo = tool.getNamespace() + '/' + tool.getName();
        final String repoUrl = QUAY_URL + "repository/" + repo;
        final Optional<String> asStringBuilds = ResourceUtilities.asString(repoUrl, quayToken.getContent(), client);

        final List<Tag> tags = new ArrayList<>();

        if (asStringBuilds.isPresent()) {
            final String json = asStringBuilds.get();
            // LOG.info(json);

            Gson gson = new Gson();
            Map<String, Map<String, Map<String, String>>> map = new HashMap<>();
            map = (Map<String, Map<String, Map<String, String>>>) gson.fromJson(json, map.getClass());

            final Map<String, Map<String, String>> listOfTags = map.get("tags");

            for (Entry<String, Map<String, String>> stringMapEntry : listOfTags.entrySet()) {
                final String s = gson.toJson(stringMapEntry.getValue());
                try {
                    final Tag tag = objectMapper.readValue(s, Tag.class);
                    tags.add(tag);
                    // LOG.info(gson.toJson(tag));
                } catch (IOException ex) {
                    LOG.info(quayToken.getUsername() + " Exception: {}", ex);
                }
            }

        }
        return tags;
    }

    @Override
    public List<String> getNamespaces() {
        List<String> namespaces = new ArrayList<>();

        UserApi api = new UserApi(apiClient);
        try {
            final UserView loggedInUser = api.getLoggedInUser();
            final List organizations = loggedInUser.getOrganizations();
            for (Object organization : organizations) {
                Map<String, String> organizationMap = (Map) organization;
                namespaces.add(organizationMap.get("name"));
            }
        } catch (ApiException e) {
            LOG.info(quayToken.getUsername() + " Exception: {}", e);
        }

        namespaces.add(quayToken.getUsername());
        return namespaces;
    }

    @Override
    public List<Tool> getContainers(List<String> namespaces) {
        List<Tool> toolList = new ArrayList<>(0);

        for (String namespace : namespaces) {
            String url = QUAY_URL + "repository?namespace=" + namespace;
            Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);
            LOG.info(quayToken.getUsername() + " : RESOURCE CALL: {}", url);

            if (asString.isPresent()) {
                RepoList repos;
                try {
                    // interesting, this relies upon our container object having the same fields
                    // as quay.io's repositories

                    // PLEASE NOTE : is_public is from quay.  It has NO connection to our is_published!
                    repos = objectMapper.readValue(asString.get(), RepoList.class);

                    List<Tool> tools = repos.getRepositories();
                    // tag all of these with where they came from
                    tools.stream().forEach(container -> container.setRegistry(Registry.QUAY_IO));
                    // not quite correct, they could be mixed but how can we tell from quay?
                    tools.stream().forEach(container -> container.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS));
                    toolList.addAll(tools);
                } catch (IOException ex) {
                    LOG.info(quayToken.getUsername() + " Exception: {}", ex);
                }
            }
        }

        return toolList;
    }

    @Override
    public Map<String, ArrayList<?>> getBuildMap(List<Tool> allRepos) {
        final SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        final Map<String, ArrayList<?>> mapOfBuilds = new HashMap<>();

        // Go through each container for each namespace
        final Gson gson = new Gson();
        for (final Tool tool : allRepos) {

            if (tool.getRegistry() != Registry.QUAY_IO) {
                continue;
            }

            final String repo = tool.getNamespace() + '/' + tool.getName();
            final String path = quayToken.getTokenSource() + '/' + repo;
            tool.setPath(path);

            LOG.info(quayToken.getUsername() + " : ========== Configuring {} ==========", path);
            // if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
            // checkTriggers(tool);
            // if (tool.hasValidTrigger()) {

            updateContainersWithBuildInfo(formatter, mapOfBuilds, gson, tool, repo, path);
            // }
            // }
        }
        return mapOfBuilds;
    }

    /**
     * For a given tool, update its registry, git, and build information with information from quay.io
     * 
     * @param formatter
     * @param mapOfBuilds
     * @param gson
     * @param tool
     * @param repo
     * @param path
     */
    private void updateContainersWithBuildInfo(SimpleDateFormat formatter, Map<String, ArrayList<?>> mapOfBuilds, Gson gson,
            Tool tool, String repo, String path) {
        // Get the list of builds from the tool.
        // Builds contain information such as the Git URL and tags
        // TODO: work with quay.io to get a better approach such as only the last build for each tag or at the very least paging
        String urlBuilds = QUAY_URL + "repository/" + repo + "/build/?limit=2147483647";
        Optional<String> asStringBuilds = ResourceUtilities.asString(urlBuilds, quayToken.getContent(), client);
        LOG.info(quayToken.getUsername() + " RESOURCE CALL: {}", urlBuilds);

        String gitURL = "";

        if (asStringBuilds.isPresent()) {
            String json = asStringBuilds.get();

            // parse json using Gson to get the git url of repository and the list of tags
            Map<String, ArrayList> map = new HashMap<>();
            map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());
            ArrayList builds = map.get("builds");
            if (builds.size() > 0) {

                mapOfBuilds.put(path, builds);

                if (!builds.isEmpty()) {
                    Map<String, Map<String, String>> map2 = (Map<String, Map<String, String>>) builds.get(0);

                    Map<String, String> triggerMetadata = map2.get("trigger_metadata");

                    if (triggerMetadata != null) {
                        gitURL = triggerMetadata.get("git_url");
                    }

                    Map<String, String> map3 = (Map<String, String>) builds.get(0);
                    String lastBuild = map3.get("started");
                    LOG.info(quayToken.getUsername() + " : LAST BUILD: {}", lastBuild);

                    Date date;
                    try {
                        date = formatter.parse(lastBuild);
                        tool.setLastBuild(date);
                    } catch (ParseException ex) {
                        LOG.info(quayToken.getUsername() + ": "  + quayToken.getUsername() + " Build date did not match format 'EEE, d MMM yyyy HH:mm:ss Z'");
                    }
                }
                if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                    tool.setRegistry(Registry.QUAY_IO);
                    tool.setGitUrl(gitURL);
                }
            }
        }
    }

    /**
     * Get the map of the given Quay tool
     * Todo: this should be implemented with the Quay API, but they currently don't have a return model for this call
     * @param tool
     * @return
         */
    public Map<String, Object> getQuayInfo(final Tool tool){
        final String repo = tool.getNamespace() + '/' + tool.getName();
        final String repoUrl = QUAY_URL + "repository/" + repo;
        final Optional<String> asStringBuilds = ResourceUtilities.asString(repoUrl, quayToken.getContent(), client);

        if (asStringBuilds.isPresent()) {
            final String json = asStringBuilds.get();

            Gson gson = new Gson();
            Map<String, Object> map = new HashMap<>();
            map = (Map<String,Object>) gson.fromJson(json, map.getClass());
            return map;

        }
        return null;
    }
}
