package io.dockstore.webservice.core.dto;

import java.util.ArrayList;
import java.util.List;

import io.dockstore.common.Registry;

public class TrsImage {
    private final long versionId;
    private final Registry registryHost;
    private final String imageName;
    private final String repository;
    private final List<TrsChecksum> checksums = new ArrayList<>();

    public TrsImage(final long versionId, final Registry imageData, final String imageName, final String repository) {
        this.versionId = versionId;
        this.registryHost = imageData;
        this.imageName = imageName;
        this.repository = repository;
    }

    public long getVersionId() {
        return versionId;
    }

    public Registry getRegistryHost() {
        return registryHost;
    }

    public String getImageName() {
        return imageName;
    }

    public String getRepository() {
        return repository;
    }

    public List<TrsChecksum> getChecksums() {
        return checksums;
    }
}
