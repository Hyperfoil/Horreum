package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class LabelValues {
    public List<LabelValue> values;

    public LabelValues() {}

    public LabelValues(List<LabelValue> v) {
       values = v;
    }

    public static List<LabelValues> parse(List<JsonNode> nodes) {
        if(nodes == null || nodes.isEmpty())
            return new ArrayList<>();

        List<LabelValues> fps = new ArrayList<>();
        nodes.forEach( n -> fps.add( new LabelValues( LabelValue.parse(n))));
        return fps;
    }
}
