package io.dockstore.webservice.permissions;

import java.util.Objects;

/**
 * Contains an email and the associated {@link Role} for that
 * email.
 * <p>
 * The email is not necessarily a single user; it can be a group of
 * users.
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
