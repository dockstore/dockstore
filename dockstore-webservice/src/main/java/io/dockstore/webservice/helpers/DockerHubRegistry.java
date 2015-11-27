package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A no-op interface intended as a place-holder for where we will implemnt docker hub functionality when they get around to exposing and
 * implementing their API.
 * 
 * @author dyuen
 */
public class DockerHubRegistry implements ImageRegistryInterface {

    public DockerHubRegistry() {
    }

    @Override
    public List<Tag> getTags(Container container) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getNamespaces() {
        return new ArrayList<>();
    }

    @Override
    public List<Container> getContainers(List<String> namespaces) {
        return new ArrayList<>();
    }

    @Override
    public Map<String, ArrayList<?>> getBuildMap(Token githubToken, Token bitbucketToken, List<Container> allRepos) {
       return new HashMap<>();
    }
}
