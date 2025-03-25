package io.hyperfoil.tools.horreum.api.changes;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.databind.node.ObjectNode;

@Schema(type = SchemaType.OBJECT, description = "Change's target")
public class Target {
    @Schema(type = SchemaType.STRING, description = "concatenated simicolons varibleID;{fingeprintJson}")
    public String target;
    public String type;
    public String refId;
    public String data = "";
    @Schema(type = SchemaType.OBJECT)
    public ObjectNode payload = null;

    public Target() {
    }

    public Target(String target, String type, String refId) {
        this.target = target;
        this.type = type;
        this.refId = refId;
    }
}
