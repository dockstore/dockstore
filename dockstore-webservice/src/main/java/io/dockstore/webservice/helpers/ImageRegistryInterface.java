package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Tag;

import java.util.List;

/**
 * Interface for how to grab data from a registry for docker containers.
 * 
 * @author dyuen
 */
public interface ImageRegistryInterface {

    /**
     * Get all tags for a given container
     * 
     * @return a list of tags for image that this points to
     */
    List<Tag> getTags(Container container);

    /**
     * Get the list of namespaces and organizations that the user is associated to on Quay.io.
     *
     * @return list of namespaces
     */
    List<String> getNamespaces();

    /**
     *
     * @param namespaces
     * @return
     */
    List<Container> getContainers(List<String> namespaces);

}
