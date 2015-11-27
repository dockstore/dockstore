package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.resources.ResourceUtilities;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.dockstore.webservice.Helper.RepoList;

/**
 * @author dyuen
 */
public class QuayImageRegistry implements ImageRegistryInterface {

    public static final String QUAY_URL = "https://quay.io/api/v1/";

    private static final Logger LOG = LoggerFactory.getLogger(QuayImageRegistry.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final Token quayToken;

    public QuayImageRegistry(HttpClient client, ObjectMapper objectMapper, Token quayToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.quayToken = quayToken;
    }

    @Override
    public List<Tag> getTags(Container container) {
        LOG.info("======================= Getting tags for: {}================================", container.getPath());
        final String repo = container.getNamespace() + "/" + container.getName();
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

            for (Map.Entry<String, Map<String, String>> stringMapEntry : listOfTags.entrySet()) {
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
            LOG.info("RESOURCE CALL: " + url);
            Gson gson = new Gson();

            Map<String, ArrayList> map = new HashMap<>();
            map = (Map<String, ArrayList>) gson.fromJson(response, map.getClass());
            List organizations = map.get("organizations");

            for (int i = 0; i < organizations.size(); i++) {
                Map<String, String> map2 = (Map<String, String>) organizations.get(i);
                LOG.info("Organization: " + map2.get("name"));
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
                    repos = objectMapper.readValue(asString.get(), RepoList.class);
                    LOG.info("RESOURCE CALL: " + url);

                    List<Container> containers = repos.getRepositories();
                    containerList.addAll(containers);
                } catch (IOException ex) {
                    LOG.info("Exception: " + ex);
                }
            }
        }

        return containerList;
    }
}
