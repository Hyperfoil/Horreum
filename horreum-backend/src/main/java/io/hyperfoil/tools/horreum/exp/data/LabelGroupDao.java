package io.hyperfoil.tools.horreum.exp.data;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.hyperfoil.tools.horreum.exp.valid.ValidLabel;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "exp_labelgroup")
@DiscriminatorColumn(name = "type")
@DiscriminatorValue("group")
public class LabelGroupDao extends PanacheEntity implements Comparable<LabelGroupDao> {

    @JsonIgnore
    public String owner; //PoC stub for the Horreum owner concept

    @NotNull
    @Column(name = "name", unique = false)
    public String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @OneToMany(cascade = { CascadeType.PERSIST,
            CascadeType.MERGE }, fetch = FetchType.EAGER, orphanRemoval = false, mappedBy = "group")
    public List<@NotNull(message = "null labels are not supported") @ValidLabel LabelDao> labels;

    public LabelGroupDao() {
        this.labels = new ArrayList<>();
    }

    public LabelGroupDao(String name) {
        this.name = name;
        this.labels = new ArrayList<>();
    }

    public int size() {
        return labels.size();
    }

    public boolean canLoad(LabelGroupDao group) {
        Set<String> names = this.labels.stream().map(l -> l.name).collect(Collectors.toSet());
        return !group.labels.stream().anyMatch(l -> names.contains(l.name));
    }

    public List<LabelDao> getConflicts(LabelGroupDao group) {
        Set<String> names = this.labels.stream().map(l -> l.name).collect(Collectors.toSet());
        return group.labels.stream().filter(l -> names.contains(l.name)).toList();
    }

    public boolean loadGroup(LabelGroupDao group) {
        Map<String, LabelDao> sources = new HashMap<>();
        labels.forEach(l -> {
            sources.put(l.name, l);
            sources.put(l.getFqdn(), l);
        });
        if (!canLoad(group)) {
            return false;
        } else {
            group.labels.forEach(l -> {
                LabelDao copy = l.copy(sources::get);

            });
        }
        return true;
    }

    public boolean hasTempLabel(ExtractorDao e) {
        return ExtractorDao.Type.VALUE.equals(e.type)
                && ((!e.isPersistent() && !e.targetLabel.isPersistent()) || (!e.targetLabel.group.equals(this)));
    }

    //need this to ensure ordering as the migration does not provide label order
    @PostLoad
    public void sortLabels(){
        this.labels = LabelDao.kahnDagSort(this.labels);
    }

    @PreUpdate
    @PrePersist
    public void checkLabels() {
        boolean needChecking = labels != null
                && labels.stream().flatMap(l -> l.extractors.stream()).anyMatch(this::hasTempLabel);
        if (needChecking) {
            Map<String, LabelDao> sources = new HashMap<>();
            labels.stream().forEach(l -> {
                sources.put(l.name, l);
                sources.put(l.getFqdn(), l);
            });
            labels.stream().flatMap(l -> l.extractors.stream())
                    .filter(this::hasTempLabel)
                    .forEach(e -> {
                        String targetName = e.targetLabel.name;
                        if (sources.containsKey(targetName)) {
                            e.targetLabel = sources.get(targetName);
                        }

                    });
        }
        //Collections.sort(labels);
        this.labels = LabelDao.kahnDagSort(this.labels);
    }

    public boolean sameOwner(String owner) {
        return owner != null && owner.equals(this.owner);
    }

    @Override
    public int compareTo(LabelGroupDao o) {
        return 0;//TODO implement comparison
    }
}
