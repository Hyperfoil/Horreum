package io.hyperfoil.tools.horreum.exp.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(
        name = "exp_labelset",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"uri"})
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LabelSetDao extends PanacheEntity {

    String uri; //String for now - maybe we want to implement a global URI object
    String name;

    @OneToMany(cascade = {CascadeType.PERSIST,CascadeType.MERGE}, fetch = FetchType.EAGER)
    @JoinColumn(name = "id")
    Set<LabelSetEntry> labels;


    @Entity
    @Table(
            name = "exp_label_set_entry",
            uniqueConstraints = {
                    @UniqueConstraint(columnNames = {"uri", "version"})
            }
    )
    public static class LabelSetEntry extends PanacheEntity {
        String uri;
        Integer version;

        @OneToOne(cascade = {CascadeType.PERSIST,CascadeType.MERGE})
        LabelDAO label;

    }
}
