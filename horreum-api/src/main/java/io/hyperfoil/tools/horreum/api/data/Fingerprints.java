package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(type = SchemaType.OBJECT,
        description = "A list of Fingerprints representing one dataset")
public class Fingerprints {

    @Schema(type = SchemaType.ARRAY, implementation = FingerprintValue.class)
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
