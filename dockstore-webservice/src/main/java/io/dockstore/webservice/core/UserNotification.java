package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;

@Schema(description = "Notifications for a user")
@Entity
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.UserNotification.findByUser",
        query = "SELECT u FROM UserNotification u WHERE u.user = :user ORDER BY u.dbCreateDate DESC"),
    @NamedQuery(name = "io.dockstore.webservice.core.UserNotification.getCountByUser", query = "SELECT COUNT(u) FROM UserNotification u WHERE u.user = :user"),
})
public abstract class UserNotification extends AbstractNotification {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", referencedColumnName = "id", columnDefinition = "bigint")
    @Schema(description = "The Dockstore user that the action belongs to")
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "The recommended action for the repository", requiredMode = RequiredMode.REQUIRED)
    private Action action;

    public UserNotification() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public enum Action {
        INFER_DOCKSTORE_YML
    }
}
