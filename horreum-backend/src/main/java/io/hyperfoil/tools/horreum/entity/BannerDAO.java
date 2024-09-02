package io.hyperfoil.tools.horreum.entity;

import static jakarta.persistence.GenerationType.SEQUENCE;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "Banner")
public class BannerDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "bannerGenerator", sequenceName = "banner_seq")
    @GeneratedValue(strategy = SEQUENCE, generator = "bannerGenerator")
    public Integer id;

    public Instant created;

    @NotNull
    public boolean active;

    @NotNull
    public String severity;

    @NotNull
    public String title;

    public String message;
}
