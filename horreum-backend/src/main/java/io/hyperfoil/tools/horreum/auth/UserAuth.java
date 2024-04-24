package io.hyperfoil.tools.horreum.auth;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.util.Set;

/**
 * Stripped down version of UserInfo entity to be used with quarkus JPA security.
 * This entity should go on a different persistence unit with privileged database access, avoiding row level security.
 */
@Entity
@Table(name = "userinfo")
@UserDefinition
@Cacheable
public class UserAuth extends PanacheEntityBase {
    
    @Id
    @NotNull
    @Username
    public String username;

    @Password String password;

    @Roles
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "userinfo_roles",
            joinColumns = @JoinColumn(name = "username"),
            uniqueConstraints = @UniqueConstraint(columnNames = { "username", "role" })
    )
    @Column(name = "role")
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    public Set<UserRole> roles;

    public UserAuth() {}

}
