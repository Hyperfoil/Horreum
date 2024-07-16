package io.hyperfoil.tools.horreum.exp.data;

import jakarta.persistence.*;

@Entity
@Table(
    name = "exp_label_value_pointer",
    //this is a lot of indexes, are they really necessary?
    indexes = {
            @Index(name="lvp_child_label_id",columnList = "child_label_id",unique = false),
            @Index(name="lvp_child_run_id",columnList = "child_run_id",unique = false),
            @Index(name="lvp_target_label_id",columnList = "target_label_id",unique = false)
    }
)
public class LabelValuePointerDao {

    @Id
    public Long childIndex;

    @Id
    @ManyToOne(cascade = CascadeType.PERSIST,fetch = FetchType.EAGER)
    @JoinColumn(name="child_label_id", referencedColumnName = "label_id")
    @JoinColumn(name="child_run_id", referencedColumnName = "run_id")
    public LabelValueDao child;

    @Id
    public Long targetIndex;

    @Id
    @ManyToOne(cascade = CascadeType.PERSIST,fetch = FetchType.EAGER)
    @JoinColumn(name="target_label_id", referencedColumnName = "label_id")
    @JoinColumn(name="target_run_id", referencedColumnName = "run_id")
    public LabelValueDao target;

    public static LabelValuePointerDao create(long childIndex, LabelValueDao child, long targetIndex, LabelValueDao target){
        LabelValuePointerDao rtrn = new LabelValuePointerDao();
        rtrn.child = child;
        rtrn.childIndex = childIndex;
        rtrn.target = target;
        rtrn.targetIndex = targetIndex;
        return rtrn;
    }

    @Override
    public String toString(){
        return childIndex+" -> target=[run_id="+target.run.id+" label_id="+target.label.id+" index="+targetIndex+"]";
    }
}
