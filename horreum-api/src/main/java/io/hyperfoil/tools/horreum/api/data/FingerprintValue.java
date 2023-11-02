package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(type = SchemaType.OBJECT,
        description = "Representation of Fingerprint. If the Fingerprint has children the value will be null.")
public class FingerprintValue {
    @Schema(description = "Fingerprint name", example = "Mode")
    public String name;
    @Schema(description = "Fingerprint name", example = "Library")
    public String value;
    @Schema(description = "List of Fingerprint children")
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
