package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dockstore.common.Partner;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@ApiModel(value = "CloudInstance", description = "Instances that launch-with cloud partners have")
@Entity
@Table(name = "cloud_instance", uniqueConstraints = @UniqueConstraint(name = "unique_user_instances", columnNames = {"url", "user_id",
    "partner"}))
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.CloudInstance.findAllWithoutUser", query = "SELECT ci from CloudInstance ci where ci.user is null")
})
public class CloudInstance implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    @ApiModelProperty(value = "The CloudInstance ID", accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    private long id;

    // TODO: This needs to be an enum
    // @Column(name = "partner", nullable = false, columnDefinition = "enum('GALAXY', 'TERRA', 'DNA_STACK', 'DNA_NEXUS', 'CGC', 'NHLBI_BIODATA_CATALYST', 'ANVIL')") did not work
    @Column(name = "partner", nullable = false)
    @ApiModelProperty(value = "Name of the partner")
    @Enumerated(EnumType.STRING)
    private Partner partner;

    @Column(name = "url", nullable = false)
    @ApiModelProperty(value = "The URL of the launch-with partner's private cloud instance")
    private String url;

    @Column(name = "display_name", nullable = false)
    @ApiModelProperty(value = "User friendly display name")
    private String displayName;

    @Column(name = "supports_http_imports")
    @ApiModelProperty(value = "Whether the CloudInstance supports http imports or not")
    private boolean supportsHttpImports;

    @Column(name = "supports_file_imports")
    @ApiModelProperty(value = "Whether the CloudInstance supports file imports or not")
    private boolean supportsFileImports;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", columnDefinition = "bigint")
    @JsonIgnore
    private User user;

    @ElementCollection(targetClass = Language.class)
    @ApiModelProperty(value = "The languages the cloud instance is known to support")
    private Set<Language> supportedLanguages;

    @Column(updatable = false)
    @CreationTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Partner getPartner() {
        return partner;
    }

    public void setPartner(Partner partner) {
        this.partner = partner;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isSupportsHttpImports() {
        return supportsHttpImports;
    }

    public void setSupportsHttpImports(boolean supportsHttpImports) {
        this.supportsHttpImports = supportsHttpImports;
    }

    public boolean isSupportsFileImports() {
        return supportsFileImports;
    }

    public void setSupportsFileImports(boolean supportsFileImports) {
        this.supportsFileImports = supportsFileImports;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Set<Language> getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(Set<Language> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
