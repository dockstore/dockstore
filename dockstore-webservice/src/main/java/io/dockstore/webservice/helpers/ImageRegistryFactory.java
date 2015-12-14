package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.Token;

/**
 * Create image registries
 *
 * @author dyuen
 */
public class ImageRegistryFactory {

    private final HttpClient client;
    private final Token quayToken;
    private final ObjectMapper objectMapper;

    public ImageRegistryFactory(final HttpClient client, final ObjectMapper objectMapper, final Token quayToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.quayToken = quayToken;
    }

    public List<ImageRegistryInterface> getAllRegistries() {
        List<ImageRegistryInterface> interfaces = new ArrayList<>();
        for (Registry r : Registry.values()) {
            interfaces.add(createImageRegistry(r));
        }
        return interfaces;
    }

    public ImageRegistryInterface createImageRegistry(Registry registry) {
        if (registry == Registry.QUAY_IO) {
            return new QuayImageRegistry(client, objectMapper, quayToken);
        } else if (registry == Registry.DOCKER_HUB) {
            return new DockerHubRegistry(client);
        } else {
            throw new CustomWebApplicationException("Sorry, we do not support " + registry + ".", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
