package io.dockstore.webservice.permissions;

import java.util.Objects;

/**
 * Contains an email and the associated {@link Role} for that
 * email.
 * <p>
 * The email value is not necessarily for a single user; it can be an email of a group. In some
 * implementations of {@link PermissionsInterface}, the value is a Dockstore username, not an email.
 * We should rename the field, but that modifies the OpenAPI, which requires a new UI build to use
 * it, and doesn't seem worth it at this point, given that only the
 * {@link io.dockstore.webservice.permissions.sam.SamPermissionsImpl} implementation is used in
 * production. The other implementations are for testing only.
 */
public class Permission {
    private String email;
    private Role role;

    public Permission() {
    }

    public Permission(String email, Role role) {
        this.email = email;
        this.role = role;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Permission that = (Permission)o;
        return Objects.equals(email, that.email) && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, role);
    }
}
