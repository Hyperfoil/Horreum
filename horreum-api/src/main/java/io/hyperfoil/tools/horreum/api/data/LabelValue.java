package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class LabelValue {
    public String name;
    public String value;

    public LabelValue() {}

    public static List<LabelValue> parse(JsonNode base) {
        List<LabelValue> values = new ArrayList<>();
        base.fieldNames().forEachRemaining( n-> {
            LabelValue v = new LabelValue();
            v.name = n;
            v.value = base.path(v.name).textValue();
            values.add(v);
        });
        return values;
    }
}
