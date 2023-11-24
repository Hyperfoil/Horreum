package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Schema(type = SchemaType.OBJECT,
        description = "Representation of Fingerprint. If the Fingerprint has children the value will be null.")
public class FingerprintValue <T> {
    @Schema(description = "Fingerprint name", example = "Mode")
    public String name;
    @Schema(description = "Fingerprint name", example = "Library")
    public T value;
    @Schema(description = "List of Fingerprint children")
    public List<FingerprintValue<?>> children;

    public FingerprintValue() {
    }

    public static List<FingerprintValue> parse(JsonNode base) {
        List<FingerprintValue> fpvs = new ArrayList<>();
        base.fieldNames().forEachRemaining( name -> {
            FingerprintValue v;
            JsonNode node = base.path(name);
            if(node.isValueNode()) {
                switch (base.path(name).getNodeType()){
                    case BINARY:
                        v = new FingerprintValue<byte[]>();
                        break;
                    case BOOLEAN:
                        v = new FingerprintValue<Boolean>();
                        v.value = node.asBoolean();
                        break;
                    case NULL:
                        v = new FingerprintValue();
                        v.value = null;
                        break;
                    case NUMBER:
                        //TODO:: How to determine Number type from JsonNode?
                        v = new FingerprintValue<Number>();
                        v.value = node.asDouble();
                        break;
                    case POJO:
                        v = new FingerprintValue<Object>();
                        v.value = node;
                        break;
                    case STRING:
                        v = new FingerprintValue<String>();
                        v.value = node.asText();
                        break;
                    default:
                        v = new FingerprintValue();
                        break;
                }
            } else {
                v = new FingerprintValue();
                v.children = parse(node);
            }
            v.name = name;
            fpvs.add(v);
        });
        return fpvs;
    }
}
