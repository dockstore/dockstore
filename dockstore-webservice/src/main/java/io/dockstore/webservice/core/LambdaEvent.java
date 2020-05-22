package io.dockstore.webservice.core;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes events triggered by GitHub webhooks
 */
@ApiModel("LambdaEvent")
@Entity
@Table(name = "LambdaEvent")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.LambdaEvent.findByRepository", query = "SELECT lambdaEvent FROM LambdaEvent lambdaEvent WHERE lambdaEvent.repository = :repository"),
        @NamedQuery(name = "io.dockstore.webservice.core.LambdaEvent.findByOrganization", query = "SELECT lambdaEvent FROM LambdaEvent lambdaEvent WHERE lambdaEvent.repository like :organization"),
        @NamedQuery(name = "io.dockstore.webservice.core.LambdaEvent.findByUsername", query = "SELECT lambdaEvent FROM LambdaEvent lambdaEvent WHERE lambdaEvent.githubUsername = :username"),
        @NamedQuery(name = "io.dockstore.webservice.core.LambdaEvent.findByUser", query = "SELECT lambdaEvent FROM LambdaEvent lambdaEvent WHERE lambdaEvent.user = :user"),
})
@SuppressWarnings("checkstyle:magicnumber")
public class LambdaEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Unique ID of the event.", position = 0)
    private long id;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The repository from the event.", required = true, position = 1)
    private String repository;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The name of the user on GitHub that triggers the event.", required = true, position = 2)
    private String githubUsername;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The git reference from the event.", required = true, position = 3)
    private String reference;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @ApiModelProperty(value = "Whether or not the event was successful.", position = 4)
    private boolean success = true;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The message associated with the event.", position = 5)
    private String message;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @ApiModelProperty(value = "Whether or not the user has dismissed the event.", position = 6)
    private boolean dismissed = false;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The type of event.", required = true, position = 7)
    private LambdaEventType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", referencedColumnName = "id")
    @ApiModelProperty(value = "User that the event is acting on (if exists in Dockstore).", position = 8)
    @JsonIgnore
    private User user;

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public LambdaEventType getType() {
        return type;
    }

    public void setType(LambdaEventType type) {
        this.type = type;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public enum LambdaEventType {
        PUSH,
        DELETE,
        INSTALL
    }

}
