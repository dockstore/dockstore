package io.dockstore.webservice.core;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

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
public class LambdaEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Unique ID of event.", position = 0)
    private long id;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The repository from the event.", position = 1)
    private String repository;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The name of the user on GitHub that triggers the event.", position = 2)
    private String username;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The git reference from the event.", position = 3)
    private String reference;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @ApiModelProperty(value = "Whether or not the event was successful.", position = 4)
    private boolean success = true;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The message associated with the event.", position = 5)
    private String message;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @ApiModelProperty(value = "Whether or not the user has dismissed an event.", position = 4)
    private boolean dismissed = false;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
}
