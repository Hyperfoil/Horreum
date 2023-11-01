package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class Fingerprints {

    public List<FingerprintValue> values;

    public Fingerprints() {
        values = new ArrayList<>();
    }

    public Fingerprints(List<FingerprintValue> parse) {
        values = parse;
    }

    public static List<Fingerprints> parse(List<JsonNode> nodes) {
        if(nodes == null || nodes.isEmpty())
            return new ArrayList<>();

        List<Fingerprints> fps = new ArrayList<>();
        nodes.forEach( n -> fps.add( new Fingerprints( FingerprintValue.parse(n))));
        return fps;
    }
}
