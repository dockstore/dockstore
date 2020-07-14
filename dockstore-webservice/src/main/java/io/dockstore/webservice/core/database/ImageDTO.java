package io.dockstore.webservice.core.database;

import java.util.List;

import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Checksum;

public class ImageDTO {
    private final long id;
    private final long versionId;
    private final Registry registryHost;
    private final String imageName;
    private final String repository;
    private final String tag;
    private final List<Checksum> checksums;

    public ImageDTO(final long id, final long versionId, final Registry imageData, final String imageName, final String repository,
            final String tag, List<Checksum> checksums) {
        this.id = id;
        this.versionId = versionId;
        this.registryHost = imageData;
        this.imageName = imageName;
        this.repository = repository;
        this.tag = tag;
        this.checksums = checksums;
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

    public String getTag() {
        return tag;
    }

    public List<Checksum> getChecksums() {
        return checksums;
    }
}
