package io.hyperfoil.tools.horreum.exp.data;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.hyperfoil.tools.horreum.api.exp.data.Label;
import io.hyperfoil.tools.horreum.exp.valid.ValidTarget;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "exp_label")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LabelDao extends PanacheEntity implements Comparable<LabelDao> {

    @Pattern(regexp = "^[^{].*[^}]$", message = "Label names cannot start with '{' or end with '}'")
    @Pattern(regexp = "^[^$].+", message = "Label name cannot start with '$'")
    @Pattern(regexp = ".*(?<!\\[])$", message = "Label name cannot end with '[]'")
    public String name;

    @NotNull(message = "label must reference a group")
    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinColumn(name = "group_id")
    @JsonIgnore
    public LabelGroupDao group;

    @ManyToOne(cascade = { CascadeType.ALL })
    public LabelDao originalLabel; //where this label was copied from

    @ManyToOne(cascade = { CascadeType.ALL })
    public LabelDao sourceLabel; //the label that substitutes for the Run from the perspective of this run

    @ManyToOne(cascade = { CascadeType.ALL })
    public LabelGroupDao sourceGroup; //

    @ManyToOne(cascade = { CascadeType.ALL })
    public LabelGroupDao targetGroup;

    @ElementCollection(fetch = FetchType.EAGER)
    @OneToMany(cascade = { CascadeType.PERSIST,
            CascadeType.MERGE }, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "parent")
    public List<@NotNull(message = "null extractors are not supported") @ValidTarget ExtractorDao> extractors;

    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    public LabelReducerDao reducer;

    @Enumerated(EnumType.STRING)
    public Label.MultiIterationType multiType = Label.MultiIterationType.Length;

    @Enumerated(EnumType.STRING)
    public Label.ScalarVariableMethod scalarMethod = Label.ScalarVariableMethod.First;

    public boolean splitting;
    public boolean dirty;

    public LabelDao() {
    }

    public LabelDao(String name) {
        this(name, null);
    }

    public LabelDao(String name, LabelGroupDao group) {
        this.name = name;
        this.group = group;
        this.extractors = new ArrayList<>();
        this.splitting = false;
        this.dirty = false;
    }

    //based on https://github.com/williamfiset/Algorithms/blob/master/src/main/java/com/williamfiset/algorithms/graphtheory/Kahns.java
    public static List<LabelDao> kahnDagSort(List<LabelDao> labels) {
        Map<String, AtomicInteger> inDegrees = new HashMap<>();
        if (labels == null || labels.isEmpty()) {
            return labels;
        }
        labels.forEach(l -> {
            inDegrees.put(l.name, new AtomicInteger(0));
        });
        labels.forEach(l -> {
            l.extractors.stream()
                    .filter(e -> ExtractorDao.Type.VALUE.equals(e.type))
                    .forEach(lve -> {
                        if (inDegrees.containsKey(lve.targetLabel.name)) {
                            inDegrees.get(lve.targetLabel.name).incrementAndGet();
                        }
                    });
        });
        Queue<LabelDao> q = new ArrayDeque<>();
        labels.forEach(l -> {
            if (inDegrees.get(l.name).get() == 0) {
                q.offer(l);
            }
        });
        List<LabelDao> rtrn = new ArrayList<>();
        while (!q.isEmpty()) {
            LabelDao l = q.poll();
            rtrn.add(l);
            l.extractors.stream()
                    .filter(e -> ExtractorDao.Type.VALUE.equals(e.type))
                    .forEach(lve -> {
                        if (inDegrees.containsKey(lve.targetLabel.name)) {
                            int newDegree = inDegrees.get(lve.targetLabel.name).decrementAndGet();
                            if (newDegree == 0) {
                                q.offer(lve.targetLabel);
                            }
                        }
                    });
        }
        int sum = inDegrees.values().stream().map(a -> a.get()).reduce((a, b) -> a + b).get();
        if (sum > 0) {
            //this means there are loops!!
            labels.forEach(l -> {
                if (inDegrees.get(l.name).get() > 0) {
                    rtrn.add(0, l);//they will then go to the back
                }
            });
        }
        //reverse because of graph direction
        Collections.reverse(rtrn);
        return rtrn;
    }

    public LabelDao copy(Function<String, LabelDao> resolver) {
        LabelDao newLabel = new LabelDao();
        newLabel.originalLabel = this;
        newLabel.name = this.name;
        newLabel.group = this.group;
        newLabel.multiType = this.multiType;
        newLabel.scalarMethod = this.scalarMethod;
        if (hasReducer()) {
            newLabel.setReducer(this.reducer.function);
        }
        if (this.hasSourceLabel()) {
            newLabel.sourceLabel = resolver.apply(this.sourceLabel.getFqdn());
        }
        List<ExtractorDao> newExtractors = new ArrayList<>();
        for (ExtractorDao e : extractors) {
            newExtractors.add(e.copy(resolver));
        }
        newLabel.loadExtractors(newExtractors);
        return newLabel;
    }

    public String getFqdn() {
        return (sourceLabel != null ? sourceLabel.getFqdn() + ":" : "") + name;
    }

    public boolean isCopy() {
        return originalLabel != null;
    }

    public boolean isSplitting() {
        return splitting;
    }

    public boolean hasReducer() {
        return reducer != null;
    }

    public boolean hasSourceLabel() {
        return sourceLabel != null;
    }

    public boolean hasSourceGroup() {
        return sourceGroup != null;
    }

    public LabelDao setSplitting(boolean splitting) {
        this.splitting = splitting;
        return this;
    }

    public LabelDao loadExtractors(ExtractorDao... extractors) {
        this.extractors = Arrays.asList(extractors);
        this.extractors.forEach(e -> e.parent = this);
        return this;
    }

    public LabelDao loadExtractors(List<ExtractorDao> extractors) {
        this.extractors = extractors;
        this.extractors.forEach(e -> e.parent = this);
        return this;
    }

    public LabelDao setTargetSchema(LabelGroupDao targetSchema) {
        this.targetGroup = targetSchema;
        return this;
    }

    public LabelDao setReducer(String javascript) {
        LabelReducerDao reducer = new LabelReducerDao(javascript);
        this.reducer = reducer;
        return this;
    }

    public LabelDao setReducer(LabelReducerDao reducer) {
        this.reducer = reducer;
        return this;
    }

    public void unloadGroup(LabelGroupDao group) {
        LabelDao.find("from LabelDao l where l.group=?1 and l.groupSource=?2 and l.group=?3", this.group, this, group)
                .stream().forEach(found -> {
                    this.group.labels.remove(found);
                });
    }

    public void loadGroup(LabelGroupDao group) {
        Map<String, LabelDao> scope = new HashMap<>();
        scope.put(this.getFqdn(), this);
        scope.put(this.name, this);
        //they need to be sorted to ensure extractor dependencies are available
        List<LabelDao> sorted = LabelDao.kahnDagSort(group.labels);
        for (LabelDao groupLabel : sorted) {
            LabelDao copy = groupLabel.copy(scope::get);
            scope.put(copy.getFqdn(), copy);
            scope.put(copy.name, copy);
            copy.sourceLabel = this;
            copy.extractors.forEach(extractor -> {
                if (ExtractorDao.Type.PATH.equals(extractor.type)) {
                    extractor.type = ExtractorDao.Type.VALUE;
                    extractor.targetLabel = this;
                }
                //VALUE extractor targetLabel were handled by Extractor.copy
                //what to do about METADATA?
            });
            if (copy.group != null) {
                copy.sourceGroup = copy.group;
            }
            if (this.group != null) {
                copy.group = this.group;
                this.group.labels.add(copy);
            }
        }
    }

    @Override
    public String toString() {
        return "label=[name:" + name + " id:" + id + " extractors="
                + (extractors == null ? "null" : extractors.stream().map(e -> e.name).collect(Collectors.toList())) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LabelDao)) {
            return false;
        }
        LabelDao o1 = (LabelDao) o;
        boolean rtrn = Objects.equals(this.id, o1.id) && this.name.equals(o1.name) && Objects.equals(this.group, o1.group);
        return rtrn;
    }

    @Override
    public int compareTo(LabelDao o1) {
        int rtrn = 0;
        if (o1.usesLabelValueExtractor() && !this.usesLabelValueExtractor()) {
            rtrn = -1;//o1 is less than 02
        } else if (this.usesLabelValueExtractor() && !o1.usesLabelValueExtractor()) {
            rtrn = 1;//o2 is less than o1
        } else if (o1.dependsOn(this)) {
            rtrn = -1;//o1 has to come after o2
        } else if (this.dependsOn(o1)) {
            rtrn = 1;//o1 must come before o2
        } else if (this.labelValueExtractorCount() > o1.labelValueExtractorCount()) {
            rtrn = 1;
        } else if (o1.labelValueExtractorCount() > this.labelValueExtractorCount()) {
            rtrn = -1;
        } else {
            //unable to compare them, assume "equal" rank?
        }
        return rtrn;
    }

    public long labelValueExtractorCount() {
        return extractors.stream().filter(e -> ExtractorDao.Type.VALUE.equals(e.type)).count();
    }

    public long forEachCount() {
        return extractors.stream().filter(e -> e.forEach).count();
    }

    public boolean hasForEach() {
        return extractors.stream().anyMatch(e -> e.forEach);
    }

    public boolean usesOnlyJsonpathExtractor() {
        return extractors.stream().allMatch(e -> ExtractorDao.Type.PATH.equals(e.type));
    }

    public boolean usesLabelValueExtractor() {
        return extractors.stream().anyMatch(e -> ExtractorDao.Type.VALUE.equals(e.type));
    }

    public boolean dependsOn(LabelDao l) {
        //do not replace id == l.id with .equals because id can be null
        return extractors.stream().anyMatch(
                e -> ExtractorDao.Type.VALUE.equals(e.type) && e.targetLabel.id == l.id && (e).targetLabel.name.equals(l.name));
    }

    /**
     * returns true if this is part of a circular reference
     * @return
     */
    @JsonIgnore
    public boolean isCircular() {
        if (!usesLabelValueExtractor()) {
            return false;
        }
        Queue<LabelDao> todo = new PriorityQueue<>();
        extractors.stream()
                .filter(e -> ExtractorDao.Type.VALUE.equals(e.type))
                .map(e -> e.targetLabel).forEach(todo::add);
        LabelDao target;
        boolean ok = true;
        while (ok && (target = todo.poll()) != null) {
            if (this.equals(target)) {
                ok = false;
            }
            if (target.extractors != null) {
                List<LabelDao> targetLabels = target.extractors.stream()
                        .filter(e -> ExtractorDao.Type.VALUE.equals(e.type))
                        .map(e -> e.targetLabel)
                        .toList();
                todo.addAll(targetLabels);
            } else {
                //extractors can be null for auto-created labels inside LabelValueExtactor :(
            }
        }
        return !ok;
    }

}
