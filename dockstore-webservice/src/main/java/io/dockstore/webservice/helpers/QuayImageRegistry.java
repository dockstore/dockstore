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

import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.docker.DockerManifestList;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.api.BuildApi;
import io.swagger.quay.client.api.ManifestApi;
import io.swagger.quay.client.api.RepositoryApi;
import io.swagger.quay.client.api.TagApi;
import io.swagger.quay.client.api.UserApi;
import io.swagger.quay.client.model.InlineResponse2002;
import io.swagger.quay.client.model.QuayBuild;
import io.swagger.quay.client.model.QuayBuildTriggerMetadata;
import io.swagger.quay.client.model.QuayOrganization;
import io.swagger.quay.client.model.QuayRepo;
import io.swagger.quay.client.model.QuayRepoManifest;
import io.swagger.quay.client.model.QuayTag;
import io.swagger.quay.client.model.UserView;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class QuayImageRegistry extends AbstractImageRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(QuayImageRegistry.class);
    private static final Gson GSON = new Gson();

    private final Token quayToken;
    private final BuildApi buildApi;
    private final RepositoryApi repositoryApi;
    private final UserApi userApi;
    private final TagApi tagApi;
    private final ManifestApi manifestApi;

    public QuayImageRegistry(final Token quayToken) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        this.quayToken = quayToken;
        if (quayToken != null) {
            apiClient.addDefaultHeader("Authorization", "Bearer " + quayToken.getContent());
        }
        this.buildApi = new BuildApi(apiClient);
        this.repositoryApi = new RepositoryApi(apiClient);
        this.userApi = new UserApi(apiClient);
        this.tagApi = new TagApi((apiClient));
        this.manifestApi = new ManifestApi(apiClient);
    }

    public QuayImageRegistry() {
        this(null);
    }

    public List<QuayTag> getAllQuayTags(String repository) throws ApiException {
        List<QuayTag> allQuayTags = new ArrayList<>();
        // Completely arbitrary maxPageSize in the weird event that Quay.io's pagination results in an infinite loop or something
        final int maxPageSize = 100;
        for (int page = 1; page < Integer.MAX_VALUE; page++) {
            InlineResponse2002 inlineResponse2002 = tagApi.listRepoTags(repository, page, maxPageSize, null, true);
            List<QuayTag> quayTags = inlineResponse2002.getTags();
            allQuayTags.addAll(quayTags);
            if (!inlineResponse2002.isHasAdditional()) {
                break;
            }
        }
        return allQuayTags;
    }

    public Optional<QuayTag> getQuayTag(String repository, String tag) throws ApiException {
        InlineResponse2002 inlineResponse2002 = tagApi.listRepoTags(repository, null, null, tag, true);
        List<QuayTag> quayTags = inlineResponse2002.getTags();
        if (!quayTags.isEmpty()) {
            return Optional.of(quayTags.get(0));
        }
        return Optional.empty();
    }


    @Override
    public List<Tag> getTags(Tool tool) {
        LOG.info(quayToken.getUsername() + " ======================= Getting tags for: {}================================", tool.getPath());
        final String repo = tool.getNamespace() + '/' + tool.getName();
        final List<Tag> tags = new ArrayList<>();
        final Optional<QuayRepo> toolFromQuay = getToolFromQuay(tool);
        if (toolFromQuay.isPresent()) {
            List<QuayTag> quayTags;
            try {
                quayTags = getAllQuayTags(repo);
            } catch (ApiException e) {
                LOG.error("Could not get Quay Tag", e);
                throw new CustomWebApplicationException("Could not get Quay Tag", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            // Search through the Quay tags for ones that are classified as a manifest list and then use its digest to get the repo's manifest.
            List<QuayTag> cleanedQuayTagList = new ArrayList<>(quayTags);
            Map<QuayTag, Set<Image>> multiImageQuayTags = new HashMap<>();
            // Process multi-arch images first to clean list of all Quay tags
            quayTags.forEach(quayTag -> {
                if (Boolean.TRUE.equals(quayTag.isIsManifestList())) {
                    try {
                        LOG.info(quayToken.getUsername() + " ======================= Getting image for tag {}================================", quayTag.getName());
                        // Store the collected Image(s) into a map that consists of <Original Manifest List Quay Tag, Set<Images>>.
                        LanguageHandlerInterface.DockerSpecifier specifier = getSpecifierFromTagName(quayTag.getName());
                        Set<Image> images = handleMultiArchQuayTags(repo, quayTag, cleanedQuayTagList, specifier);
                        multiImageQuayTags.put(quayTag, images);
                    } catch (ApiException ex) {
                        LOG.info("Unable to handle manifest list for Quay Tag " + quayTag.getName() + " in repo " + repo, ex);
                    }
                }
            });

            for (QuayTag tagItem : cleanedQuayTagList) {
                try {
                    final Tag tag = convertQuayTagToTag(tagItem, tool, multiImageQuayTags);
                    tags.add(tag);
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    LOG.error(quayToken.getUsername() + " Exception: {}", ex);
                }
            }
        }
        String repository = tool.getNamespace() + "/" + tool.getName();
        updateTagsWithBuildInformation(repository, tags, tool);

        return tags;
    }

    /**
     * For each manifest in the list:
     * <li>Create an image</li>
     * <li>Check if there's a matching Quay tag in cleanedQuayTagList using the manifest digest. If there's a match, this means that the multi-arch
     * image was built using the docker manifest method which creates individual images for each architecture.
     * There would be no match if the multi-arch image was created using the buildx method.</li>
     * <li>Remove the matching Quay tag from cleanedQuayTagList if it exists because it's already been processed and an image was created for it.
     * Removing it ensures that a Dockstore version is not created from it.</li>
     *
     * @param repository a tool from Dockstore
     * @param quayTag    the Quay Tag that is a manifest list
     * @param cleanedQuayTagList List of Quay Tags that removes Quay tags that are listed in quayTag's manifest list.
     * @return list of images with arch/os information for the quayTag
     */
    public Set<Image> handleMultiArchQuayTags(String repository, QuayTag quayTag, List<QuayTag> cleanedQuayTagList, LanguageHandlerInterface.DockerSpecifier specifier) throws ApiException {
        QuayRepoManifest quayRepoManifest;
        DockerManifestList manifestList;
        Set<Image> images = new HashSet<>();
        quayRepoManifest = manifestApi.getRepoManifest(quayTag.getManifestDigest(), repository);
        try {
            manifestList = GSON.fromJson(quayRepoManifest.getManifestData(), DockerManifestList.class);
        } catch (JsonSyntaxException ex) {
            LOG.info("Unexpected response from Quay while retrieving repo manifest for Quay Tag " + quayTag.getName() + " for repository " + repository, ex);
            return images;
        }

        try {
            // Create an image for each architecture manifest
            Arrays.stream(manifestList.getManifests()).forEach(manifest -> {
                final String manifestDigest = manifest.getDigest();
                final String imageID = quayTag.getImageId();
                List<Checksum> checksums = new ArrayList<>();
                String[] splitChecksum = manifestDigest.split(":");
                checksums.add(new Checksum(splitChecksum[0], splitChecksum[1]));
                Image image = new Image(checksums, repository, quayTag.getName(), imageID, Registry.QUAY_IO, manifest.getSize(), quayTag.getLastModified());
                image.setArchitecture(manifest.getPlatform().getArchitecture());
                image.setOs(manifest.getPlatform().getOs());
                image.setSpecifier(specifier);
                images.add(image);
                // Look through Quay tags to see if this multi arch image was built using the "docker manifest" approach where each architecture has its own tag
                Optional<QuayTag> singleArchTag = cleanedQuayTagList.stream().filter(tag -> tag.getManifestDigest().equals(manifestDigest)).findFirst();
                singleArchTag.ifPresent(cleanedQuayTagList::remove); // Remove it because it's already been processed
            });
        } catch (NullPointerException ex) {
            LOG.info("Unexpected null response from Quay while retrieving image and checksum info from manifest for Quay Tag " + quayTag.getName() + " for repository " + repository, ex);
        }
        return images;
    }

    private Tag convertQuayTagToTag(QuayTag quayTag, Tool tool, Map<QuayTag, Set<Image>> multiImageQuayTags) throws InvocationTargetException, IllegalAccessException {
        final Tag tag = new Tag();
        BeanUtils.copyProperties(tag, quayTag);
        // If we were unable to get the list of images from the manifest list, then an empty image list will be added to the tag.
        if (quayTag.isIsManifestList() != null && quayTag.isIsManifestList()) {
            Set<Image> images = multiImageQuayTags.get(quayTag);
            tag.getImages().addAll(images);
        } else {
            LOG.info(quayToken.getUsername() + " ======================= Getting image for tag {}================================", tag.getName());
            final String repo = tool.getNamespace() + '/' + tool.getName();
            final LanguageHandlerInterface.DockerSpecifier specifier = getSpecifierFromTagName(quayTag.getName());
            Image tagImage = getImageForTag(repo, quayTag, specifier);
            tag.getImages().add(tagImage);
        }
        insertQuayLastModifiedIntoLastBuilt(quayTag, tag);
        return tag;
    }

    /**
     * Checks if a quay tag is multi-arch by getting its repo manifest. Checking its repo manifest instead of QuayTag ensures that isIsManifestList() is not null
     * if there's no authentication.
     * @param quayTag
     * @param repository
     * @return
     */
    public boolean isMultiArchImage(QuayTag quayTag, String repository) throws ApiException {
        QuayRepoManifest quayRepoManifest = manifestApi.getRepoManifest(quayTag.getManifestDigest(), repository);
        return quayRepoManifest.isIsManifestList();
    }

    //TODO: If the repo has a lot of tags, then it needs to be paged through. Can get tag info individually, but then that's more API calls.
    public Image getImageForTag(final String repo, final QuayTag quayTag, final LanguageHandlerInterface.DockerSpecifier specifier) {
        final String manifestDigest = quayTag.getManifestDigest();
        final String imageID = quayTag.getImageId();
        List<Checksum> checksums = new ArrayList<>();
        checksums.add(new Checksum(manifestDigest.split(":")[0], manifestDigest.split(":")[1]));
        Image image = new Image(checksums, repo, quayTag.getName(), imageID, Registry.QUAY_IO, quayTag.getSize(), quayTag.getLastModified());
        image.setSpecifier(specifier);
        return image;
    }

    /**
     * Return information from Quay that describes a tool.
     * @param tool a tool from Dockstore
     * @return corresponding QuayRepo information from quay.io
     */
    public Optional<QuayRepo> getToolFromQuay(final Tool tool) {
        final String repo = tool.getNamespace() + '/' + tool.getName();

        try {
            final QuayRepo quayRepo = repositoryApi.getRepo(repo, false);
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

        try {
            final UserView loggedInUser = userApi.getLoggedInUser();
            final List<QuayOrganization> organizations = loggedInUser.getOrganizations();
            namespaces = organizations.stream().map(QuayOrganization::getName).collect(Collectors.toList());
        } catch (ApiException e) {
            LOG.error(quayToken.getUsername() + " Exception: {}", e);
        }

        namespaces.add(quayToken.getUsername());
        return namespaces;
    }

    public List<String> getRepositoryNamesFromNamespace(String namespace) {
        try {
            List<QuayRepo> repositories = repositoryApi.listRepos(null, null, null, null, null, null, namespace).getRepositories();
            return repositories.stream().map(QuayRepo::getName).collect(Collectors.toList());
        } catch (ApiException e) {
            LOG.error("Could not retrieve repositories for: " + namespace, e);
            return new ArrayList<>();
        }
    }


    @Override
    public List<Tool> getToolsFromNamespace(List<String> namespaces) {
        List<Tool> toolList = new ArrayList<>(0);

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
                tools.forEach(container -> container.setRegistry(Registry.QUAY_IO.getDockerPath()));
                // not quite correct, they could be mixed but how can we tell from quay?
                tools.forEach(container -> container.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS));
                toolList.addAll(tools);
            } catch (ApiException | IllegalAccessException | InvocationTargetException ex) {
                LOG.warn(quayToken.getUsername() + " Exception: {}", ex);
            }
        }

        return toolList;
    }

    public Tool getToolFromNamespaceAndRepo(String namespace, String repository) {
        try {
            String name = namespace + "/" + repository;
            QuayRepo repo = repositoryApi.getRepo(name, true);

            Tool tool = new Tool();
            // interesting, this relies upon our container object having the same fields
            // as quay.io's repositories

            // PLEASE NOTE : is_public is from quay.  It has NO connection to our is_published!
            tool.setName(repo.getName());
            tool.setNamespace(repo.getNamespace());
            // tag all of these with where they came from
            tool.setRegistry(Registry.QUAY_IO.getDockerPath());
            // not quite correct, they could be mixed but how can we tell from quay?
            tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
            return tool;
        } catch (ApiException ex) {
            LOG.warn(quayToken.getUsername() + " Exception: {}", ex);
            throw new CustomWebApplicationException("Could not get repository from Quay.io", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    public void updateAPIToolsWithBuildInformation(List<Tool> apiTools) {
        // Initialize useful classes
        final SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        // Grab build information for given repository
        try {
            for (Tool tool : apiTools) {
                // Set path information (not sure why we have to do this here)
                final String repo = tool.getNamespace() + '/' + tool.getName();
                LOG.info("Grabbing tool information for " + tool.getPath());
                // Initialize giturl
                String gitUrl = null;

                final List<QuayBuild> builds = buildApi.getRepoBuilds(repo, null, 1).getBuilds();
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
                                    Map<String, String> map = GSON.fromJson(substring, new TypeToken<Map<String, String>>() {
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
                    tool.setRegistry(Registry.QUAY_IO.getDockerPath());
                    tool.setGitUrl(gitUrl);
                }
            }
        } catch (ApiException e) {
            LOG.error(quayToken.getUsername() + ": could not process builds to determine build information", e);
        }
    }

    private void updateTagsWithBuildInformation(String repository, List<Tag> tags, Tool tool) {
        // Grab build information for given repository
        // List of builds for a tool
        try {
            final List<QuayBuild> builds = buildApi.getRepoBuilds(repository, null, Integer.MAX_VALUE).getBuilds();

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
            LOG.error(quayToken.getUsername() + ": could not process builds", e);
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
        try {
            final List<QuayBuild> builds = buildApi.getRepoBuilds(repo, null, Integer.MAX_VALUE).getBuilds();
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
            LOG.error(quayToken.getUsername() + ": could not process builds to determine mode", e);
        }
        return false;
    }
}
