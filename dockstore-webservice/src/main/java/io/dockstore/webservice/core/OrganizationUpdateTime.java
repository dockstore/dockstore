package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * Class used to display organizations on the Dockstore homepage
 * @author aduncan
 * @since 1.8.0
 */
public class OrganizationUpdateTime {
    private String name;
    private String displayName;
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Date lastUpdateDate;

    public OrganizationUpdateTime(String name, String displayName, Date lastUpdateDate) {
        this.name = name;
        this.displayName = displayName;
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
}
