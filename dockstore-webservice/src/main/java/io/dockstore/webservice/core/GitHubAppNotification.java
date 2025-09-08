package io.dockstore.webservice.core;

import io.dockstore.common.SourceControl;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Schema(description = "Notifications for a GitHub App repository")
@Entity
@Table(name = "github_app_notification")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.GitHubAppNotification.getLatestByRepository",
        query = "SELECT u from GitHubAppNotification u WHERE u.sourceControl = :sourcecontrol AND u.organization = :organization AND u.repository = :repository ORDER BY u.dbCreateDate DESC"),
    @NamedQuery(name = "io.dockstore.webservice.core.GitHubAppNotification.getCountByUser", query = "SELECT COUNT(u) FROM GitHubAppNotification u WHERE u.user = :user")
})
public class GitHubAppNotification extends UserNotification {

    @Column(nullable = false, columnDefinition = "text")
    @Convert(converter = SourceControlConverter.class)
    @Schema(description = "The source control provider", requiredMode = RequiredMode.REQUIRED)
    private SourceControl sourceControl;

    @Column(nullable = false)
    @Schema(description = "The git organization name", requiredMode = RequiredMode.REQUIRED)
    private String organization;

    @Column(nullable = false)
    @Schema(description = "The git repository name", requiredMode = RequiredMode.REQUIRED)
    private String repository;

    public GitHubAppNotification() {
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public void setSourceControl(SourceControl sourceControl) {
        this.sourceControl = sourceControl;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }
}
