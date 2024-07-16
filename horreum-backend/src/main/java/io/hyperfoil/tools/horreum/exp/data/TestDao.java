package io.hyperfoil.tools.horreum.exp.data;

import io.hyperfoil.tools.horreum.exp.valid.ValidLabel;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "exp_test"
)
public class TestDao extends PanacheEntity {

    public String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "test_labels")
    @OneToMany(cascade = {CascadeType.PERSIST,CascadeType.MERGE}, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "parent")
    //Tried ManyToMany but it resulted in Labels not cascading persist
    public List<@NotNull(message="null labels are not supported") @ValidLabel LabelDAO> labels;

    public TestDao(){}
    public TestDao(String name){
        this.name = name;
    }
    public TestDao loadLabels(@Valid LabelDAO...definitions){
        this.labels = new ArrayList(Arrays.asList(definitions));
        checkLabels();
        return this;
    }

    public void copyLabelSet(String uri){
        List<LabelSetDao> labelSet = LabelSetDao.find("uri", uri).list();
        labelSet.forEach(labelSet1 -> {
            this.loadLabels(labelSet1.labels.stream().map(lse -> lse.label.copy(this)).toArray(LabelDAO[]::new));
        });
    }
    public boolean hasTempLabel(ExtractorDao e){
        return ExtractorDao.Type.VALUE.equals(e.type)
        && (
            (
                !e.isPersistent()
                && !e.targetLabel.isPersistent()
            )
            || (
                !e.targetLabel.parent.equals(this)
            )
        );
    }
    //based on https://github.com/williamfiset/Algorithms/blob/master/src/main/java/com/williamfiset/algorithms/graphtheory/Kahns.java
    public void kahnDagSort(){
        Map<String, AtomicInteger> inDegrees = new HashMap<>();
        if(labels == null || labels.isEmpty()){
            return;
        }
        labels.forEach(l->{
            inDegrees.put(l.name,new AtomicInteger(0));
        });
        labels.forEach(l->{
            l.extractors.stream()
                    .filter(e-> ExtractorDao.Type.VALUE.equals(e.type))
                    .forEach(lve->{
                        if(inDegrees.containsKey(lve.targetLabel.name)) {
                            inDegrees.get(lve.targetLabel.name).incrementAndGet();
                        }
                    });
        });
        Queue<LabelDAO> q = new ArrayDeque<>();
        labels.forEach(l->{
            if(inDegrees.get(l.name).get()==0){
                q.offer(l);
            }
        });
        List<LabelDAO> rtrn = new ArrayList<>();
        while(!q.isEmpty()){
            LabelDAO l = q.poll();
            rtrn.add(l);
            l.extractors.stream()
                    .filter(e-> ExtractorDao.Type.VALUE.equals(e.type))
                    .forEach(lve->{
                        if(inDegrees.containsKey(lve.targetLabel.name)) {
                            int newDegree = inDegrees.get(lve.targetLabel.name).decrementAndGet();
                            if (newDegree == 0) {
                                q.offer(lve.targetLabel);
                            }
                        }
                    });
        }
        int sum = inDegrees.values().stream().map(a->a.get()).reduce((a,b)->a+b).get();
        if(sum > 0){
            //this means there are loops!!
            labels.forEach(l->{
                if(inDegrees.get(l.name).get() > 0){
                    rtrn.add(0,l);//they will then go to the back
                }
            });
        }
        //reverse because of graph direction
        Collections.reverse(rtrn);
        labels = rtrn;
    }


    @PreUpdate
    @PrePersist
    public void checkLabels(){
        boolean needChecking = labels != null && labels.stream().flatMap(l -> l.extractors.stream()).anyMatch(this::hasTempLabel);
        if(needChecking){
            Map<String,LabelDAO> byName = labels.stream().collect(Collectors.toMap(l->l.name,l->l));
            labels.stream().flatMap(l->l.extractors.stream())
                    .filter(this::hasTempLabel)
                    .forEach(e->{
                        String targetName = e.targetLabel.name;
                        if(byName.containsKey(targetName)){
                            e.targetLabel = byName.get(targetName);
                        }

                    });
        }
        //Collections.sort(labels);
        kahnDagSort();
    }
}
