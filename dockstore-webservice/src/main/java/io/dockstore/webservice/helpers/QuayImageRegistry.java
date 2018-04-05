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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.resources.ResourceUtilities;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.api.UserApi;
import io.swagger.quay.client.model.UserView;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class QuayImageRegistry extends AbstractImageRegistry {

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
            Gson gson = new Gson();
            Map<String, Map<String, Map<String, String>>> map = new HashMap<>();
            map = (Map<String, Map<String, Map<String, String>>>)gson.fromJson(json, map.getClass());

            final Map<String, Map<String, String>> listOfTags = map.get("tags");

            for (Entry<String, Map<String, String>> stringMapEntry : listOfTags.entrySet()) {
                final String s = gson.toJson(stringMapEntry.getValue());
                try {
                    final Tag tag = objectMapper.readValue(s, Tag.class);
                    tags.add(tag);
                } catch (IOException ex) {
                    LOG.warn(quayToken.getUsername() + " Exception: {}", ex);
                }
            }

        }

        String repository = tool.getNamespace() + "/" + tool.getName();
        updateTagsWithBuildInformation(repository, tags, tool);

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
                Map<String, String> organizationMap = (Map)organization;
                namespaces.add(organizationMap.get("name"));
            }
        } catch (ApiException e) {
            LOG.warn(quayToken.getUsername() + " Exception: {}", e);
        }

        namespaces.add(quayToken.getUsername());
        return namespaces;
    }

    @Override
    public List<Tool> getToolsFromNamespace(List<String> namespaces) {
        List<Tool> toolList = new ArrayList<>(0);

        for (String namespace : namespaces) {
            String url = QUAY_URL + "repository?namespace=" + namespace;
            Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);
            //            LOG.info(quayToken.getUsername() + " : RESOURCE CALL: {}", url);

            if (asString.isPresent()) {
                RepoList repos;
                try {
                    // interesting, this relies upon our container object having the same fields
                    // as quay.io's repositories

                    // PLEASE NOTE : is_public is from quay.  It has NO connection to our is_published!
                    repos = objectMapper.readValue(asString.get(), RepoList.class);

                    List<Tool> tools = repos.getRepositories();
                    // tag all of these with where they came from
                    tools.stream().forEach(container -> container.setRegistry(Registry.QUAY_IO.toString()));
                    // not quite correct, they could be mixed but how can we tell from quay?
                    tools.stream().forEach(container -> container.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS));
                    toolList.addAll(tools);
                } catch (IOException ex) {
                    LOG.warn(quayToken.getUsername() + " Exception: {}", ex);
                }
            }
        }

        return toolList;
    }

    @Override
    public void updateAPIToolsWithBuildInformation(List<Tool> apiTools) {
        // Initialize useful classes
        final Gson gson = new Gson();
        final SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        for (Tool tool : apiTools) {
            // Set path information (not sure why we have to do this here)
            final String repo = tool.getNamespace() + '/' + tool.getName();

            LOG.info("Grabbing tool information for " + tool.getPath());

            // Initialize giturl
            String gitUrl = null;

            // Make call for build information from quay (only need most recent)
            String urlBuilds = QUAY_URL + "repository/" + repo + "/build/?limit=1";
            Optional<String> asStringBuilds = ResourceUtilities.asString(urlBuilds, quayToken.getContent(), client);

            // Check result of API call
            if (asStringBuilds.isPresent()) {
                String json = asStringBuilds.get();

                // Store the json file into a map for parsing
                Map<String, ArrayList> buildMap = new HashMap<>();
                buildMap = (Map<String, ArrayList>)gson.fromJson(json, buildMap.getClass());

                // Grad build information
                ArrayList builds = buildMap.get("builds");

                if (builds.size() > 0) {
                    // Look at the latest build for the git url
                    // ASSUMPTION : We are assuming that for a given Quay repo users are only using one git trigger
                    if (!builds.isEmpty()) {
                        // If a build exists, grab data from it and update the tool
                        Map<String, Map<String, String>> individualBuild = (Map<String, Map<String, String>>)builds.get(0);

                        // Get the git url
                        Map<String, String> triggerMetadata = individualBuild.get("trigger_metadata");

                        if (triggerMetadata != null) {
                            gitUrl = triggerMetadata.get("git_url");
                        }
                        // alternative hack for GA4GH importer (should be removed if we can create triggers on quay.io repos)
                        String autoGenerateTag = "GA4GH-generated-do-not-edit";
                        try {
                            if (tool.getDescription().contains(autoGenerateTag)) {
                                String[] split = tool.getDescription().split("\n");
                                for (String line : split) {
                                    if (line.contains(autoGenerateTag)) {
                                        String[] splitLine = line.split("<>");
                                        String trimmed = splitLine[1].trim();
                                        // strip the brackets
                                        String substring = trimmed.substring(1, trimmed.length() - 1);
                                        Map<String, String> map = new Gson().fromJson(substring,
                                                new TypeToken<Map<String, String>>() { }.getType());
                                        gitUrl = "git@github.com:" + map.get("namespace") + "/" + map.get("repo") + ".git";
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOG.info("Found GA4GH tag in description for " + tool.getPath() + " but could not process it into a git url");
                        }

                        // Get lastbuild time
                        Map<String, String> individualBuildStringMap = (Map<String, String>)builds.get(0);
                        String lastBuild = individualBuildStringMap.get("started");

                        Date date;
                        try {
                            date = formatter.parse(lastBuild);
                            tool.setLastBuild(date);
                        } catch (ParseException ex) {
                            LOG.warn(quayToken.getUsername() + ": " + quayToken.getUsername()
                                    + " Build date did not match format 'EEE, d MMM yyyy HH:mm:ss Z'");
                        }
                    }

                    // Set some attributes if not manual
                    if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                        tool.setRegistry(Registry.QUAY_IO.toString());
                        tool.setGitUrl(gitUrl);
                    }
                }
            }
        }
    }

    private void updateTagsWithBuildInformation(String repository, List<Tag> tags, Tool tool) {
        final Gson gson = new Gson();

        // Grab build information for given repository
        String urlBuilds = QUAY_URL + "repository/" + repository + "/build/?limit=2147483647";
        Optional<String> asStringBuilds = ResourceUtilities.asString(urlBuilds, quayToken.getContent(), client);

        // List of builds for a tool
        ArrayList builds;

        if (asStringBuilds.isPresent()) {
            String json = asStringBuilds.get();
            Map<String, ArrayList> map = new HashMap<>();
            map = (Map<String, ArrayList>)gson.fromJson(json, map.getClass());
            builds = map.get("builds");

            // Set up tags with build information
            for (Tag tag : tags) {
                // Set tag information based on build info
                for (Object build : builds) {
                    Map<String, ArrayList<String>> tagsMap = (Map<String, ArrayList<String>>)build;
                    List<String> buildTags = tagsMap.get("tags");

                    // If build is for given tag
                    if (buildTags.contains(tag.getName())) {
                        // Find if tag has a git reference
                        Map<String, Map<String, String>> triggerMetadataMap = (Map<String, Map<String, String>>)build;
                        Map<String, String> triggerMetadata = triggerMetadataMap.get("trigger_metadata");
                        if (triggerMetadata != null) {
                            String ref = triggerMetadata.get("ref");
                            ref = parseReference(ref);
                            tag.setReference(ref);
                            if (ref == null) {
                                tag.setAutomated(false);
                            } else {
                                tag.setAutomated(true);
                            }
                        } else {
                            LOG.error(quayToken.getUsername() + " : WARNING: trigger_metadata is NULL. Could not parse to get reference!");
                        }

                        break;
                    }
                }

                // Set up default descriptor paths
                tag.setCwlPath(tool.getDefaultCwlPath());
                tag.setWdlPath(tool.getDefaultWdlPath());

                // Set up default dockerfile path
                tag.setDockerfilePath(tool.getDefaultDockerfilePath());
            }
        }

    }

    /**
     * Get the map of the given Quay tool
     * Todo: this should be implemented with the Quay API, but they currently don't have a return model for this call
     *
     * @param tool
     * @return
     */
    public Map<String, Object> getQuayInfo(final Tool tool) {
        final String repo = tool.getNamespace() + '/' + tool.getName();
        final String repoUrl = QUAY_URL + "repository/" + repo;
        final Optional<String> asStringBuilds = ResourceUtilities.asString(repoUrl, quayToken.getContent(), client);

        if (asStringBuilds.isPresent()) {
            final String json = asStringBuilds.get();

            Gson gson = new Gson();
            Map<String, Object> map = new HashMap<>();
            map = (Map<String, Object>)gson.fromJson(json, map.getClass());
            return map;

        }
        return null;
    }

    /**
     * @param reference a raw reference from git like "refs/heads/master"
     * @return the last segment like master
     */
    public static String parseReference(String reference) {
        if (reference != null) {
            Pattern p = Pattern.compile("([\\S][^/\\s]+)?/([\\S][^/\\s]+)?/(\\S+)");
            Matcher m = p.matcher(reference);
            if (!m.find()) {
                LOG.info("Cannot parse reference: {}", reference);
                return null;
            }

            // These correspond to the positions of the pattern matcher
            final int refIndex = 3;

            reference = m.group(refIndex);
            LOG.info("REFERENCE: {}", reference);
        }
        return reference;
    }

    @Override
    public Registry getRegistry() {
        return Registry.QUAY_IO;
    }

    public static class RepoList {

        private List<Tool> repositories;

        public List<Tool> getRepositories() {
            return repositories;
        }

        public void setRepositories(List<Tool> repositories) {
            this.repositories = repositories;
        }
    }
}
