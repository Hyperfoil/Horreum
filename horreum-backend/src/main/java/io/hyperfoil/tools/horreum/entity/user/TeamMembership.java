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
    public TeamRole role;

    public TeamMembership(){}

    public TeamMembership(UserInfo user, Team team, TeamRole role) {
        this.user = user;
        this.team = team;
        this.role = role;
    }

    public TeamMembership(UserInfo user, Team team, String uiRole) {
        this.user = user;
        this.team = team;
        this.role = switch (uiRole) {
            case "viewer" -> TeamRole.TEAM_VIEWER;
            case "tester" -> TeamRole.TEAM_TESTER;
            case "uploader" -> TeamRole.TEAM_UPLOADER;
            case "manager" -> TeamRole.TEAM_MANAGER;
            default -> throw new IllegalStateException("Unexpected legacy role value: " + uiRole);
        };
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TeamMembership that = (TeamMembership) o;
        return user.equals(that.user) && team.equals(that.team) && role == that.role;
    }

    @Override public int hashCode() {
        int result = user.hashCode();
        result = 31 * result + team.hashCode();
        result = 31 * result + role.hashCode();
        return result;
    }

    public String asUIRole() {
        return switch (role) {
            case TEAM_VIEWER -> "viewer";
            case TEAM_TESTER -> "tester";
            case TEAM_UPLOADER -> "uploader";
            case TEAM_MANAGER -> "manager";
        };
    }

    public String asTeam() {
        return team.teamName + "-team";
    }
    
    public String asRole() {
        return team.teamName + "-" + switch (role) {
            case TEAM_VIEWER -> "viewer";
            case TEAM_TESTER -> "tester";
            case TEAM_UPLOADER -> "uploader";
            case TEAM_MANAGER -> "manager";
        };
    }
}
