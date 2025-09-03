package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;

/**
 * Represents a site-wide notification that will be displayed in a notification bar at the top of every page
 *
 * @since 1.8.0
 */


@ApiModel("PublicNotification")
@Entity
@Table(name = "notification")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.PublicNotification.getActiveNotifications",
        query = "SELECT n FROM PublicNotification n WHERE n.expiration > CURRENT_TIMESTAMP"),
})
@SuppressWarnings("checkstyle:magicnumber")
public class PublicNotification extends AbstractNotification {

    @Column
    @Size(max = 1024) // increase for extra markdown characters such as hyper-links
    @ApiModelProperty(value = "Text content of the notification to be displayed", position = 1)
    private String message;

    @Column
    @ApiModelProperty(value = "Timestamp at which the notification is expired", position = 2, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp expiration;

    @Column
    @Enumerated(EnumType.STRING)
    @Schema(description = "Type of notification", example = "SITEWIDE")
    private Type type;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Priority level of the notification", position = 4)
    private Priority priority;  // LOW, MEDIUM, HIGH, or CRITICAL

    public PublicNotification() { }  // blank constructor called by POST request

    public PublicNotification(long id, String message, Timestamp expiration, Priority priority) {
        this.message = message;
        this.expiration = expiration;
        this.priority = priority;
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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public enum Priority {
        LOW, MEDIUM, CRITICAL
    }
}
