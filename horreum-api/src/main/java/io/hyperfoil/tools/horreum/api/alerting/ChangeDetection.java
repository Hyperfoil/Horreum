package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.horreum.api.data.changeDetection.FixedThresholdDetectionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.RelativeDifferenceDetectionConfig;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class ChangeDetection {


    @JsonProperty( required = true )
    public Integer id;
    @JsonProperty( required = true )
    public String model;
    @NotNull
    @JsonProperty( required = true )
    @Schema(type = SchemaType.OBJECT, discriminatorProperty = "model",
            discriminatorMapping = {
                    @DiscriminatorMapping(schema = RelativeDifferenceDetectionConfig.class, value = "relativeDifference"),
                    @DiscriminatorMapping(schema = FixedThresholdDetectionConfig.class, value = "fixedThreshold")
            },
        oneOf = {
                RelativeDifferenceDetectionConfig.class,
                FixedThresholdDetectionConfig.class
        }
    )
    public ObjectNode config;

    public ChangeDetection() {
    }

    public ChangeDetection(Integer id, String model, ObjectNode config) {
        this.id = id;
        this.model = model;
        this.config = config;
    }

    public String toString() {
        return "ChangeDetection{id=" + this.id + ", model='" + this.model + '\'' + ", config=" + this.config + '}';
    }
}
