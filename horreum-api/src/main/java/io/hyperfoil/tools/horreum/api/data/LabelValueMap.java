package io.hyperfoil.tools.horreum.api.data;

import java.util.HashMap;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 *
 */
@Schema(type = SchemaType.OBJECT, description = "a map of label name to value", example = "{ \"[labelName]\": labelValue}")
public class LabelValueMap extends HashMap<String, JsonNode> {

    public static LabelValueMap fromObjectNode(ObjectNode node) {
        LabelValueMap rtrn = new LabelValueMap();
        if (node != null) {
            node.fields().forEachRemaining(field -> {
                rtrn.put(field.getKey(), field.getValue());
            });
        }
        return rtrn;
    }
}
