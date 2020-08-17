package io.dockstore.webservice.core;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class LicenseInformation {
    String licenseName;

    @Column(columnDefinition = "text")
    String licenseContent;

    public String getLicenseName() {
        return licenseName;
    }

    public void setLicenseName(String licenseName) {
        this.licenseName = licenseName;
    }

    public String getLicenseContent() {
        return licenseContent;
    }

    public void setLicenseContent(String licenseContent) {
        this.licenseContent = licenseContent;
    }
}
