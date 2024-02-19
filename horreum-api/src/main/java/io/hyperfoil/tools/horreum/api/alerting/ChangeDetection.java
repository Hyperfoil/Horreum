package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class ChangeDetection {

    /* This is another hack, it is based on io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel.config
     * We should coordinate type information with that method but openapi needs statically typed classes and that method
     * dynamically builds the ConditionConfig
     */
    public static class RelativeDifferenceDetection {
        public String filter;//mean,min,max
        public Integer window;//1
        public Double threshold;//0.2
        public Integer minPrevious;//1
    }
    public static class Threshold {
        Integer value;
        Boolean enabled;
        Boolean inclusive;
    }
    public static class FixedThresholdDetection {
        public Threshold min;
        public Threshold max;
    }

    @JsonProperty( required = true )
    public Integer id;
    @JsonProperty( required = true )
    public String model;
    @NotNull
    @JsonProperty( required = true )
    @Schema(type = SchemaType.OBJECT,
        oneOf = {
                ChangeDetection.RelativeDifferenceDetection.class,
                ChangeDetection.FixedThresholdDetection.class
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
