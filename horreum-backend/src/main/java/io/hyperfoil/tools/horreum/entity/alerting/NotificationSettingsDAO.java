package io.hyperfoil.tools.horreum.entity.alerting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Settings for notification on each change if the user/team has certain {@link WatchDAO}.
 * Each user/team can have multiple notifications.
 */
@Entity(name = "notificationsettings")
public class NotificationSettingsDAO extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   /**
    * Either user name or team
    */
   @NotNull
   public String name;

   @NotNull
   @Column(columnDefinition = "boolean default false")
   public boolean isTeam;

   /**
    * E.g. email, IRC, Zulip, Slack...
    */
   @NotNull
   public String method;

   /**
    * Email address, room name...
    */
   public String data;

   @NotNull
   @Column(columnDefinition = "boolean default false")
   public boolean disabled;
}
