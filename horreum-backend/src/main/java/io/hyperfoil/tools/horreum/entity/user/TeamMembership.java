package io.hyperfoil.tools.horreum.entity.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity(name = "userinfo_teams")
public class TeamMembership extends PanacheEntityBase {

    @Id
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "username")
    public UserInfo user;

    @Id
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id")
    public Team team;

    @Id
    @Column(name = "team_role")
    @Enumerated(EnumType.STRING)
    public TeamRole roles;

    public TeamMembership(){}

    public TeamMembership(UserInfo user, Team team, TeamRole roles) {
        this.user = user;
        this.team = team;
        this.roles = roles;
    }
}
