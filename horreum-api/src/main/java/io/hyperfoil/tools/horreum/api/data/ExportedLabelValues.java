package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(type = SchemaType.OBJECT,
        description = "A list of Fingerprint values for one dataset")
public class ExportedLabelValues {
    @Schema(type = SchemaType.ARRAY, implementation = FingerprintValue.class)
    public List<FingerprintValue> values;

    public ExportedLabelValues() {}

    public ExportedLabelValues(List<FingerprintValue> v) {
       values = v;
    }

    public static List<ExportedLabelValues> parse(List<JsonNode> nodes) {
        if(nodes == null || nodes.isEmpty())
            return new ArrayList<>();

        List<ExportedLabelValues> fps = new ArrayList<>();
        nodes.forEach( n -> fps.add( new ExportedLabelValues( FingerprintValue.parse(n))));
        return fps;
    }
}
