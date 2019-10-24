package io.dockstore.webservice.core;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
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
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Notification.getActiveNotifications",
                query = "SELECT n FROM Notification n WHERE n.expiration > CURRENT_TIMESTAMP"),
})
public class Notification {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // id is auto incremented by the database
    private long id;

    @Column
    private String message;

    @Column
    private Timestamp expiration;

    @Column
    @Enumerated(EnumType.STRING)
    private Priority priority;  // LOW, MEDIUM, HIGH, or CRITICAL

    public Notification() { }  // blank constructor called by POST request

    public Notification(long id, String message, Timestamp expiration, Priority priority) {
        this.id = id;
        this.message = message;
        this.expiration = expiration;
        this.priority = priority;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        System.out.println(id);
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Timestamp getExpiration() {
        return expiration;
    }

    public void setExpiration(Timestamp expiration) {
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
