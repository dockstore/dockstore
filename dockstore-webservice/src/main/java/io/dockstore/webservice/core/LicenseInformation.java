package io.dockstore.webservice.core;

import javax.persistence.Embeddable;

@Embeddable
public class LicenseInformation {
    String licenseName;

    public String getLicenseName() {
        return licenseName;
    }

    public void setLicenseName(String licenseName) {
        this.licenseName = licenseName;
    }
}
