package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class FingerprintValue {
    public String name;
    public String value;
    public List<FingerprintValue> children;

    public FingerprintValue() {
    }

    public static List<FingerprintValue> parse(JsonNode base) {
        List<FingerprintValue> fpvs = new ArrayList<>();
        base.fieldNames().forEachRemaining( n -> {
            FingerprintValue v = new FingerprintValue();
            v.name = n;
            if(base.path(n).isTextual())
                v.value = base.path(v.name).textValue();
            else {
                v.children = parse(base.path(v.name));
            }
            fpvs.add(v);
        });
        return fpvs;
    }
}
