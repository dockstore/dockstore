/*
 *    Copyright 2019 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Pattern;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Data about versions of a workflow/tool in Dockstore rather than about the original workflow.
 *
 * Stays modifiable even when the parent (version) becomes immutable via postgres security policies, allowing
 * us to modify things like verification status, DOIs, and whether a workflow version is hidden.
 *
 * Note that this entity is not directly serialized, instead individual fields are exposed in the Version
 * model.
 */
@Entity
@Table(name = "version_metadata")
public class VersionMetadata {

    private static final String PUBLIC_ACCESSIBLE_DESCRIPTION = "Whether the version has everything needed to run without restricted access permissions";

    @Column(columnDefinition =  "boolean default false")
    protected boolean verified;

    @Column()
    protected String verifiedSource;

    @Column()
    protected String verifiedPlatforms;

    @Column()
    @Pattern(regexp = "10\\.[^/]++/.++")
    protected String doiURL;

    @Column()
    protected boolean hidden;

    @Column(columnDefinition = "text default 'NOT_REQUESTED'", nullable = false)
    @Enumerated(EnumType.STRING)
    protected Version.DOIStatus doiStatus;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
    @Schema(description = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
    protected  String description;

    @Column(name = "description_source")
    @Enumerated(EnumType.STRING)
    protected DescriptionSource descriptionSource;

    @MapsId
    @OneToOne
    @JoinColumn(name = "id")
    protected Version parent;

    // Explicit LAZY, just in case.  Currently used by nothing on the frontend or CLI.
    @ElementCollection
    @CollectionTable(
            name = "PARSED_INFORMATION",
            joinColumns = @JoinColumn(name = "VERSION_METADATA_ID")
    )
    protected List<ParsedInformation> parsedInformationSet = new ArrayList<>();

    @ElementCollection(targetClass = OrcidPutCode.class, fetch = FetchType.EAGER)
    @JoinTable(name = "version_metadata_orcidputcode", joinColumns = @JoinColumn(name = "version_metadata_id"),
            uniqueConstraints = @UniqueConstraint(name = "unique_version_metadata_user_orcidputcode", columnNames = { "version_metadata_id", "userid", "orcidputcode" }))
    @MapKeyColumn(name = "userid", columnDefinition = "bigint")
    @ApiModelProperty(value = "The presence of the put code for a userid indicates the version was exported to ORCID for the corresponding Dockstore user.")
    @Schema(description = "The presence of the put code for a userid indicates the version was exported to ORCID for the corresponding Dockstore user.")
    protected Map<Long, OrcidPutCode> userIdToOrcidPutCode = new HashMap<>();

    @Id
    @Column(name = "id")
    private long id;

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

    @Column()
    @ApiModelProperty(value = PUBLIC_ACCESSIBLE_DESCRIPTION)
    @Schema(description = PUBLIC_ACCESSIBLE_DESCRIPTION)
    private Boolean publicAccessibleTestParameterFile;

    @Column(columnDefinition = "varchar")
    @Convert(converter = DescriptorTypeVersionConverter.class)
    @ApiModelProperty(value = "The language versions for the version's descriptor files")
    private List<String> descriptorTypeVersions = new ArrayList<>();

    @Column(columnDefinition = "varchar")
    @Convert(converter = EngineVersionConverter.class)
    @ApiModelProperty(value = "The engine versions this workflow version can run on")
    private List<String> engineVersions = new ArrayList<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public List<ParsedInformation> getParsedInformationSet() {
        return parsedInformationSet;
    }

    public void setParsedInformationSet(List<ParsedInformation> parsedInformationSet) {
        this.parsedInformationSet.clear();

        // Deserializer can call this method while parsedInformationSet is null, which causes a Null Pointer Exception
        // Adding a checker here to avoid a Null Pointer Exception caused by the deserializer
        if (parsedInformationSet != null) {
            this.parsedInformationSet.addAll(parsedInformationSet);
        }
    }

    public Map<Long, OrcidPutCode> getUserIdToOrcidPutCode() {
        return userIdToOrcidPutCode;
    }

    public void setUserIdToOrcidPutCode(Map<Long, OrcidPutCode> userIdToOrcidPutCode) {
        this.userIdToOrcidPutCode = userIdToOrcidPutCode;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getPublicAccessibleTestParameterFile() {
        return publicAccessibleTestParameterFile;
    }

    public void setPublicAccessibleTestParameterFile(Boolean publicAccessibleTestParameterFile) {
        this.publicAccessibleTestParameterFile = publicAccessibleTestParameterFile;
    }

    public List<String> getDescriptorTypeVersions() {
        return descriptorTypeVersions;
    }

    public void setDescriptorTypeVersions(final List<String> descriptorTypeVersions) {
        this.descriptorTypeVersions = descriptorTypeVersions;
    }

    public List<String> getEngineVersions() {
        return engineVersions;
    }

    public void setEngineVersions(final List<String> engineVersions) {
        this.engineVersions = engineVersions;
    }
}
