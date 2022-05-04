package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The profile of a user using a token (Google profile, GitHub profile, etc)
 * The order of the properties are important, the UI lists these properties in this order.
 */
@Embeddable
public class Profile implements Serializable {
    @Column(columnDefinition = "text")
    public String name;
    @Column(columnDefinition = "text")
    @JsonView(UserProfileViews.PrivateInfo.class)
    public String email;
    @Column(columnDefinition = "text")
    public String avatarURL;
    @Column(columnDefinition = "text")
    public String company;
    @Column(columnDefinition = "text")
    public String location;
    @Column(columnDefinition = "text")
    public String bio;
    @Column(columnDefinition = "text")
    public String link;
    /**
     * Redundant with token, but needed since tokens can be deleted.
     * i.e. if usernames can change and tokens can be deleted, we need somewhere to let
     * token-less users login
     */
    @Column(columnDefinition = "text")
    public String username;
    @Column
    @JsonIgnore
    public String onlineProfileId;

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;
    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;
}
