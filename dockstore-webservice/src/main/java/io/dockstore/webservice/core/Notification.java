package io.dockstore.webservice.core;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import io.swagger.annotations.ApiModel;

/**
 * Represents a site-wide notification that will be displayed in a notification bar at the top of every page
 *
 * @since 1.8.0
 */


@ApiModel("Notification")
@Entity
@Table(name = "notification")
//@NamedQueries({
//        @NamedQuery(name = "io.dockstore.webservice.core.Notification.getActiveNotifications", query = ""),
//        @NamedQuery(name = "io.dockstore.webservice.core.Notification.getNotification", query = "")
//})
public class Notification {

    @Id
    private long id;

    @Column
    private String message;

    @Column
    private LocalDateTime expiration;

    @Column
    @Enumerated(EnumType.STRING)
    private Priority priority;

    public Notification() { }

    public Notification(long id, String message, LocalDateTime expiration, Priority priority) {
        this.id = id;
        this.message = message;
        this.expiration = expiration;
        this.priority = priority;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getExpiration() {
        return expiration;
    }

    public void setExpiration(LocalDateTime expiration) {
        this.expiration = expiration;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
