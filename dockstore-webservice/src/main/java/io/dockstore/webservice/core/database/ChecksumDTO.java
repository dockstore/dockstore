package io.dockstore.webservice.core.database;

public class ChecksumDTO {
    private final String type;
    private final String checksum;

    public ChecksumDTO(final String type, final String checksum) {
        this.type = type;
        this.checksum = checksum;
    }

    public String getType() {
        return type;
    }

    public String getChecksum() {
        return checksum;
    }
}
