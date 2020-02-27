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

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.api.BuildApi;
import io.swagger.quay.client.api.RepositoryApi;
import io.swagger.quay.client.api.UserApi;
import io.swagger.quay.client.model.QuayBuild;
import io.swagger.quay.client.model.QuayBuildTriggerMetadata;
import io.swagger.quay.client.model.QuayOrganization;
import io.swagger.quay.client.model.QuayRepo;
import io.swagger.quay.client.model.QuayTag;
import io.swagger.quay.client.model.UserView;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class QuayImageRegistry extends AbstractImageRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(QuayImageRegistry.class);

    private final Token quayToken;
    private final ApiClient apiClient;

    public QuayImageRegistry(final Token quayToken) {
        this.quayToken = quayToken;

        apiClient = Configuration.getDefaultApiClient();
        apiClient.addDefaultHeader("Authorization", "Bearer " + quayToken.getContent());
        // apiClient.setBasePath(QUAY_URL);
    }

    @Override
    public List<Tag> getTags(Tool tool) {
        LOG.info(quayToken.getUsername() + " ======================= Getting tags for: {}================================", tool.getPath());

        final List<Tag> tags = new ArrayList<>();
        final Optional<QuayRepo> toolFromQuay = getToolFromQuay(tool);
        if (toolFromQuay.isPresent()) {
            final QuayRepo quayRepo = toolFromQuay.get();
            final Map<String, QuayTag> tagsFromRepo = quayRepo.getTags();
            for (QuayTag tagItem : tagsFromRepo.values()) {
                try {
                    final Tag tag = new Tag();
                    BeanUtils.copyProperties(tag, tagItem);
                    tag.getImages().addAll(getImagesForTag(tool, tag, tagItem));
                    insertQuayLastModifiedIntoLastBuilt(tagItem, tag);
                    tags.add(tag);
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    LOG.warn(quayToken.getUsername() + " Exception: {}", ex);
                }
            }

        }

        String repository = tool.getNamespace() + "/" + tool.getName();
        updateTagsWithBuildInformation(repository, tags, tool);

        return tags;
    }

    //TODO: If the repo has a lot of tags, then it needs to be paged through. Can get tag info individually, but then that's more API calls.
    private Set<Image> getImagesForTag(final Tool tool, final Tag tag, final QuayTag quayTag) {
        LOG.info(quayToken.getUsername() + " ======================= Getting image for tag {}================================", tag.getName());

        final String repo = tool.getNamespace() + '/' + tool.getName();
        try {
            final String manifestDigest = quayTag.getManifestDigest();
            final String imageID = quayTag.getImageId();
            List<Checksum> checksums = new ArrayList<>();
            checksums.add(new Checksum(manifestDigest.split(":")[0], manifestDigest.split(":")[1]));
            return Collections.singleton(new Image(checksums, repo, tag.getName(), imageID));
        } catch (IndexOutOfBoundsException | NullPointerException ex) {
            LOG.error("Could not get checksum information for " + repo, ex);
            return Collections.emptySet();
        }
    }

    /**
     * Return information from Quay that describes a tool.
     * @param tool a tool from Dockstore
     * @return corresponding QuayRepo information from quay.io
     */
    public Optional<QuayRepo> getToolFromQuay(final Tool tool) {
        final String repo = tool.getNamespace() + '/' + tool.getName();

        RepositoryApi api = new RepositoryApi(apiClient);
        try {
            final QuayRepo quayRepo = api.getRepo(repo, false);
            return Optional.of(quayRepo);
        } catch (ApiException e) {
            LOG.error(quayToken.getUsername() + " could not read from " + repo, e);
        }
        return Optional.empty();
    }

    private void insertQuayLastModifiedIntoLastBuilt(QuayTag quayTag, Tag tag) {
        String lastModifiedValue = quayTag.getLastModified();
        if (StringUtils.isNotBlank(lastModifiedValue)) {
            try {
                tag.setLastBuilt(new StdDateFormat().parse(lastModifiedValue));
            } catch (ParseException ex) {
                LOG.error("Error reading " + lastModifiedValue, ex);
            }
        }
    }

    @Override
    public List<String> getNamespaces() {
        List<String> namespaces = new ArrayList<>();

        UserApi api = new UserApi(apiClient);
        try {
            final UserView loggedInUser = api.getLoggedInUser();
            final List<QuayOrganization> organizations = loggedInUser.getOrganizations();
            namespaces = organizations.stream().map(QuayOrganization::getName).collect(Collectors.toList());
        } catch (ApiException e) {
            LOG.warn(quayToken.getUsername() + " Exception: {}", e);
        }

        namespaces.add(quayToken.getUsername());
        return namespaces;
    }

    @Override
    public List<Tool> getToolsFromNamespace(List<String> namespaces) {
        List<Tool> toolList = new ArrayList<>(0);
        RepositoryApi repositoryApi = new RepositoryApi(apiClient);

        for (String namespace : namespaces) {
            try {
                final List<QuayRepo> quayRepos = repositoryApi.listRepos(null, null, null, null, null, null, namespace).getRepositories();
                List<Tool> tools = Lists.newArrayList();
                for (QuayRepo repo : quayRepos) {
                    Tool tool = new Tool();
                    // interesting, this relies upon our container object having the same fields
                    // as quay.io's repositories

                    // PLEASE NOTE : is_public is from quay.  It has NO connection to our is_published!
                    BeanUtils.copyProperties(tool, repo);
                    tools.add(tool);
                }
                // tag all of these with where they came from
                tools.forEach(container -> container.setRegistry(Registry.QUAY_IO.toString()));
                // not quite correct, they could be mixed but how can we tell from quay?
                tools.forEach(container -> container.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS));
                toolList.addAll(tools);
            } catch (ApiException | IllegalAccessException | InvocationTargetException ex) {
                LOG.warn(quayToken.getUsername() + " Exception: {}", ex);
            }
        }

        return toolList;
    }

    @Override
    public void updateAPIToolsWithBuildInformation(List<Tool> apiTools) {
        // Initialize useful classes
        final SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        // Grab build information for given repository
        BuildApi api = new BuildApi(apiClient);
        try {
            for (Tool tool : apiTools) {
                // Set path information (not sure why we have to do this here)
                final String repo = tool.getNamespace() + '/' + tool.getName();
                LOG.info("Grabbing tool information for " + tool.getPath());
                // Initialize giturl
                String gitUrl = null;

                final List<QuayBuild> builds = api.getRepoBuilds(repo, null, Integer.MAX_VALUE).getBuilds();
                // Check result of API call
                if (builds != null && !builds.isEmpty()) {
                    // Look at the latest build for the git url
                    // ASSUMPTION : We are assuming that for a given Quay repo users are only using one git trigger
                    // If a build exists, grab data from it and update the tool
                    final QuayBuild individualBuild = builds.get(0);
                    // Get the git url
                    final QuayBuildTriggerMetadata triggerMetadata = individualBuild.getTriggerMetadata();
                    if (triggerMetadata != null) {
                        gitUrl = triggerMetadata.getGitUrl();
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
                                    Map<String, String> map = new Gson().fromJson(substring, new TypeToken<Map<String, String>>() {
                                    }.getType());
                                    gitUrl = "git@github.com:" + map.get("namespace") + "/" + map.get("repo") + ".git";
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.info("Found GA4GH tag in description for " + tool.getPath() + " but could not process it into a git url");
                    }

                    // Get lastbuild time
                    String lastBuild = individualBuild.getStarted();

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
        } catch (ApiException e) {
            LOG.error(quayToken.getUsername() + ": could not process builds to determine build information");
        }
    }

    private void updateTagsWithBuildInformation(String repository, List<Tag> tags, Tool tool) {
        // Grab build information for given repository
        // List of builds for a tool
        BuildApi api = new BuildApi(apiClient);
        try {
            final List<QuayBuild> builds = api.getRepoBuilds(repository, null, Integer.MAX_VALUE).getBuilds();

            // Set up tags with build information
            for (Tag tag : tags) {
                // Set tag information based on build info
                for (QuayBuild build : builds) {
                    final List<String> buildTags = build.getTags();
                    // If build is for given tag
                    if (buildTags.contains(tag.getName())) {
                        // Find if tag has a git reference
                        final QuayBuildTriggerMetadata triggerMetadata = build.getTriggerMetadata();
                        if (triggerMetadata != null) {
                            String ref = triggerMetadata.getRef();
                            ref = parseReference(ref);
                            tag.setReference(ref);
                            tag.setAutomated(ref != null);
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
        } catch (ApiException e) {
            LOG.error(quayToken.getUsername() + ": could not process builds");
        }
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

    @Override
    public boolean canConvertToAuto(Tool tool) {
        final String repo = tool.getNamespace() + '/' + tool.getName();
        // Grab build information for given repository
        BuildApi api = new BuildApi(apiClient);
        try {
            final List<QuayBuild> builds = api.getRepoBuilds(repo, null, Integer.MAX_VALUE).getBuilds();
            if (!builds.isEmpty()) {
                for (QuayBuild build : builds) {
                    final QuayBuildTriggerMetadata triggerMetadata = build.getTriggerMetadata();
                    if (triggerMetadata != null) {
                        String gitUrl = triggerMetadata.getGitUrl();
                        if (Objects.equals(gitUrl, tool.getGitUrl())) {
                            return true;
                        }
                    }
                }
            }
        } catch (ApiException e) {
            LOG.error(quayToken.getUsername() + ": could not process builds to determine mode");
        }
        return false;
    }
}
