package io.hyperfoil.tools.horreum.entity.user;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;

/**
 * This entity provides the base for Horreum's security model.
 * The authentication of the user can be external or internal using the password field.
 * A user has a (possibly empty) set of "user roles" that are defined in the `roles` relation.
 * A user can also have "team roles" that are defined on the `teams` relation.
 */
@Entity
@Table(name = "userinfo")
@UserDefinition
@Cacheable
public class UserInfo extends PanacheEntityBase {
    @Id
    @NotNull
    @Username
    public String username;

    @Password
    String password;

    @Column(name = "email")
    public String email;
    @Column(name = "first_name")
    public String firstName;
    @Column(name = "last_name")
    public String lastName;

    @Roles
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(joinColumns = @JoinColumn(name = "username"), uniqueConstraints = @UniqueConstraint(columnNames = {
            "username", "role" }))
    @Column(name = "role")
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    public Set<UserRole> roles;

    public String defaultTeam;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "user")
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    public Set<TeamMembership> teams;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, mappedBy = "user")
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    public Set<UserApiKey> apiKeys;

    public UserInfo() {
    }

    public UserInfo(String username) {
        this.username = username;
        this.roles = new HashSet<>();
        this.teams = new HashSet<>();
        this.apiKeys = new HashSet<>();
    }

    public void setPassword(String clearPassword) {
        password = clearPassword == null ? null : BcryptUtil.bcryptHash(clearPassword);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserInfo userInfo = (UserInfo) o;
        return username.equals(userInfo.username)
                && Objects.equals(password, userInfo.password)
                && Objects.equals(email, userInfo.email)
                && Objects.equals(firstName, userInfo.firstName)
                && Objects.equals(lastName, userInfo.lastName);
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + Objects.hashCode(password);
        result = 31 * result + Objects.hashCode(email);
        result = 31 * result + Objects.hashCode(firstName);
        result = 31 * result + Objects.hashCode(lastName);
        return result;
    }
}
