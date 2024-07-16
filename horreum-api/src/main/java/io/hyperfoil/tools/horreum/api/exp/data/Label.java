package io.hyperfoil.tools.horreum.api.exp.data;


import java.util.List;

public class Label {

    public Long id;

    public String name;

    public Test parent;

    public String target_schema;

    public String uri;
    public String parent_uri;

    public List<Extractor> extractors;

    public LabelReducer reducer;

    public MultiIterationType multiType;
    public ScalarVariableMethod scalarMethod;

    public enum MultiIterationType { Length, NxN}
    public enum ScalarVariableMethod { First, All}

}
