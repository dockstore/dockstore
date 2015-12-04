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

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;

import io.dockstore.webservice.Helper.RepoList;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.ContainerMode;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.resources.ResourceUtilities;

/**
 * @author dyuen
 */
public class QuayImageRegistry implements ImageRegistryInterface {

    public static final String QUAY_URL = "https://quay.io/api/v1/";

    private static final Logger LOG = LoggerFactory.getLogger(QuayImageRegistry.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Token quayToken;

    public QuayImageRegistry(final HttpClient client, final ObjectMapper objectMapper, final Token quayToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.quayToken = quayToken;
    }

    @Override
    public List<Tag> getTags(Container container) {
        LOG.info("======================= Getting tags for: {}================================", container.getPath());
        final String repo = container.getNamespace() + '/' + container.getName();
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
                    LOG.info("Exception: {}", ex);
                }
            }

        }
        return tags;
    }

    @Override
    public List<String> getNamespaces() {
        List<String> namespaces = new ArrayList<>();

        String url = QUAY_URL + "user/";
        Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);
        if (asString.isPresent()) {
            String response = asString.get();
            LOG.info("RESOURCE CALL: {}", url);
            Gson gson = new Gson();

            Map<String, ArrayList> map = new HashMap<>();
            map = (Map<String, ArrayList>) gson.fromJson(response, map.getClass());
            List organizations = map.get("organizations");

            for (int i = 0; i < organizations.size(); i++) {
                Map<String, String> map2 = (Map<String, String>) organizations.get(i);
                LOG.info("Organization: {}", map2.get("name"));
                namespaces.add(map2.get("name"));
            }
        }

        namespaces.add(quayToken.getUsername());
        return namespaces;
    }

    @Override
    public List<Container> getContainers(List<String> namespaces) {
        List<Container> containerList = new ArrayList<>(0);

        for (String namespace : namespaces) {
            String url = QUAY_URL + "repository?namespace=" + namespace;
            Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);

            if (asString.isPresent()) {
                RepoList repos;
                try {
                    // interesting, this relies upon our container object having the same fields
                    // as quay.io's repositories
                    repos = objectMapper.readValue(asString.get(), RepoList.class);
                    LOG.info("RESOURCE CALL: {}", url);

                    List<Container> containers = repos.getRepositories();
                    // tag all of these with where they came from
                    containers.stream().forEach(container -> container.setRegistry(Registry.QUAY_IO));
                    // not quite correct, they could be mixed but how can we tell from quay?
                    containers.stream().forEach(container -> container.setMode(ContainerMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS));
                    containerList.addAll(containers);
                } catch (IOException ex) {
                    LOG.info("Exception: {}", ex);
                }
            }
        }

        return containerList;
    }

    @Override
    public Map<String, ArrayList<?>> getBuildMap(Token githubToken, Token bitbucketToken, List<Container> allRepos) {
        final SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        final Map<String, ArrayList<?>> mapOfBuilds = new HashMap<>();

        // Go through each container for each namespace
        final Gson gson = new Gson();
        for (final Container container : allRepos) {

            if (container.getRegistry() != Registry.QUAY_IO){
                continue;
            }

            final String repo = container.getNamespace() + '/' + container.getName();
            final String path = quayToken.getTokenSource() + '/' + repo;
            container.setPath(path);

            LOG.info("========== Configuring {} ==========", path);
            if (container.getMode() != ContainerMode.MANUAL_IMAGE_PATH) {
                updateContainersWithBuildInfo(formatter, mapOfBuilds, gson, container, repo, path);
            }

            final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(container.getGitUrl(), client,
                    bitbucketToken == null ? null : bitbucketToken.getContent(), githubToken.getContent());
            if (sourceCodeRepo != null) {
                // find if there is a Dockstore.cwl file from the git repository
                sourceCodeRepo.findCWL(container);
            }
        }
        return mapOfBuilds;
    }

    /**
     * For a given container, update its registry, git, and build information with information from quay.io
     * @param formatter
     * @param mapOfBuilds
     * @param gson
     * @param container
     * @param repo
     * @param path
     */
    private void updateContainersWithBuildInfo(SimpleDateFormat formatter, Map<String, ArrayList<?>> mapOfBuilds, Gson gson,
                                                  Container container, String repo, String path) {
        // Get the list of builds from the container.
        // Builds contain information such as the Git URL and tags
        String urlBuilds = QUAY_URL + "repository/" + repo + "/build/";
        Optional<String> asStringBuilds = ResourceUtilities.asString(urlBuilds, quayToken.getContent(), client);

        String gitURL = "";

        if (asStringBuilds.isPresent()) {
            String json = asStringBuilds.get();
            LOG.info("RESOURCE CALL: {}", urlBuilds);

            // parse json using Gson to get the git url of repository and the list of tags
            Map<String, ArrayList> map = new HashMap<>();
            map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());
            ArrayList builds = map.get("builds");

            mapOfBuilds.put(path, builds);

            if (!builds.isEmpty()) {
                Map<String, Map<String, String>> map2 = (Map<String, Map<String, String>>) builds.get(0);

                gitURL = map2.get("trigger_metadata").get("git_url");

                Map<String, String> map3 = (Map<String, String>) builds.get(0);
                String lastBuild = map3.get("started");
                LOG.info("LAST BUILD: {}", lastBuild);

                Date date;
                try {
                    date = formatter.parse(lastBuild);
                    container.setLastBuild(date);
                } catch (ParseException ex) {
                    LOG.info("Build date did not match format 'EEE, d MMM yyyy HH:mm:ss Z'");
                }
            }
        }

        container.setRegistry(Registry.QUAY_IO);
        container.setGitUrl(gitURL);
    }
}
