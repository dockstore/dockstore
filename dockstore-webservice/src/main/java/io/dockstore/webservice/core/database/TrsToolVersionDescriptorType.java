package io.dockstore.webservice.core.database;

import io.dockstore.common.DescriptorLanguage;

public class TrsToolVersionDescriptorType {

    private final long versionId;
    private final DescriptorLanguage.FileType fileType;

    public TrsToolVersionDescriptorType(final long versionId, final DescriptorLanguage.FileType fileType) {
        this.versionId = versionId;
        this.fileType = fileType;
    }

    public long getVersionId() {
        return versionId;
    }

    public DescriptorLanguage.FileType getFileType() {
        return fileType;
    }
}
