package io.hyperfoil.tools.horreum.entity.user;

import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.Objects;
import java.util.Set;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;
import static org.hibernate.id.enhanced.SequenceStyleGenerator.SEQUENCE_PARAM;

@Entity(name = "team")
public class Team extends PanacheEntityBase {

    @Id
    @GenericGenerator(
            name = "teamIdGenerator",
            type = SeqIdGenerator.class,
            parameters = { @Parameter(name = SEQUENCE_PARAM, value = "team_id_seq"), @Parameter(name = INCREMENT_PARAM, value = "1") }
    )
    @GeneratedValue(strategy = SEQUENCE, generator = "teamIdGenerator")
    @Column(name="id")
    public Integer id;

    @Column(name = "team_name")
    public String teamName;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    public Set<TeamMembership> teams;

    public Team(){}

    public Team(String teamName) {
        this.teamName = teamName;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(id, ((Team) o).id) && Objects.equals(teamName, ((Team) o).teamName);
    }

    @Override public int hashCode() {
        int result = Objects.hashCode(id);
        result = 31 * result + Objects.hashCode(teamName);
        return result;
    }
}
