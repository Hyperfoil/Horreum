package io.hyperfoil.tools.horreum.entity.data;

import static jakarta.persistence.GenerationType.SEQUENCE;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "allowedsite")
public class AllowedSiteDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "allowedsiteSequence", sequenceName = "allowedsite_seq")
    @GeneratedValue(strategy = SEQUENCE, generator = "allowedsiteSequence")
    public Long id;

    @NotNull
    public String prefix;
}
