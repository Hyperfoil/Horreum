package io.hyperfoil.tools.horreum.entity;

import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * This is an offline-cache of user-team mapping with the source-of-truth being held
 * in Keycloak. As the Keycloak API does not allow querying for users having effective
 * role (see https://issues.redhat.com/browse/KEYCLOAK-11494) we'll update these cached
 * roles as needed in UserTeamsFilter.
 * The table is actually read in {@link io.hyperfoil.tools.horreum.api.NotificationService}.
 */
@Entity(name = "userinfo")
public class UserInfo extends PanacheEntityBase {
   @Id
   @NotNull
   public String username;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(
         joinColumns = @JoinColumn(name = "username"),
         uniqueConstraints = @UniqueConstraint(columnNames = { "username", "team" })
   )
   @Column(name = "team")
   public Set<String> teams;

   public String defaultTeam;
}
