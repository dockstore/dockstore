package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.WebApplicationException;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dockstore.webservice.core.Token;

/**
 * Create image registries
 *
 * @author dyuen
 */
public class ImageRegistryFactory {
    public enum Registry {
        QUAY_IO, DOCKER_HUB
    }

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

    /**
     * TODO: stop-gap until we properly define an enum across the project
     * 
     * @param registry
     * @return
     */
    public ImageRegistryInterface createImageRegistry(String registry) {
        if (registry.isEmpty() || registry.equals(Registry.DOCKER_HUB.toString())) {
            return createImageRegistry(Registry.DOCKER_HUB);
        } else if (registry.equals("quay.io")) {
            return createImageRegistry(Registry.QUAY_IO);
        } else {
            throw new WebApplicationException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }

    public ImageRegistryInterface createImageRegistry(Registry registry) {
        if (registry == Registry.QUAY_IO) {
            return new QuayImageRegistry(client, objectMapper, quayToken);
        } else if (registry == Registry.DOCKER_HUB) {
            return new DockerHubRegistry(client);
        } else {
            throw new WebApplicationException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
