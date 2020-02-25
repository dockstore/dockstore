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
import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@SuppressWarnings("checkstyle:magicnumber")
public class Notification {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // id is auto incremented by the database
    @ApiModelProperty(value = "ID for the notification")
    private long id;

    @Column
    @Size(max = 280)
    @ApiModelProperty(value = "Text content of the notification to be displayed")
    private String message;

    @Column
    @ApiModelProperty(value = "Timestamp at which the notification is expired")
    private Timestamp expiration;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Type of notification, sitewide or newsbody")
    private Type type;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Priority level of the notification")
    private Priority priority;  // LOW, MEDIUM, HIGH, or CRITICAL

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    @ApiModelProperty(value = "Timestamp at which the notification was created")
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @ApiModelProperty(value = "Timestamp at which the notification was last updated")
    private Timestamp dbUpdateDate;

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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public enum Priority {
        LOW, MEDIUM, CRITICAL
    }

    public enum Type {
        SITEWIDE, NEWSBODY
    }
}
