package io.dockstore.webservice.core.dto;

import java.util.ArrayList;
import java.util.List;

public class TrsImage {
    private final String registryHost;
    private final String imageName;
    private final String repository;
    private final List<TrsChecksum> checksums = new ArrayList<>();

    public TrsImage(final String registryHost, final String imageName, final String repository) {
        this.registryHost = registryHost;
        this.imageName = imageName;
        this.repository = repository;
    }
}
