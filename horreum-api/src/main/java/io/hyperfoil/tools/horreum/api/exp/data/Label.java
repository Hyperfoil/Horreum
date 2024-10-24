package io.hyperfoil.tools.horreum.api.exp.data;

import java.util.List;

public class Label {

    public Long id;

    public String name;

    public LabelGroup group;
    public LabelGroup sourceGroup;
    public Label sourceLabel;
    public LabelGroup targetGroup;

    public List<Extractor> extractors;

    public LabelReducer reducer;

    public boolean splitting;
    public boolean dirty;

    public MultiIterationType multiType;
    public ScalarVariableMethod scalarMethod;

    public enum MultiIterationType {
        Length,
        NxN
    }

    public enum ScalarVariableMethod {
        First,
        All
    }

}
