package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(type = SchemaType.OBJECT,
        description = "Representation of a LabelValue")
public class ExportedLabelValue {
    @Schema(description = "Fingerprint name", example = "Throughput 8 CPU")
    public String name;
    @Schema(description = "Fingerprint value", example = "141085.5")
    public String value;

    public ExportedLabelValue() {}

    public static List<ExportedLabelValue> parse(JsonNode base) {
        List<ExportedLabelValue> values = new ArrayList<>();
        base.fieldNames().forEachRemaining( n-> {
            ExportedLabelValue v = new ExportedLabelValue();
            v.name = n;
            v.value = base.path(v.name).textValue();
            values.add(v);
        });
        return values;
    }
}
