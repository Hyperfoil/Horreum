package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class ChangeDetection {

    /* This is another hack, it is based on io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel.config
     * We should coordinate type information with that method but openapi needs statically typed classes and that method
     * dynamically builds the ConditionConfig
     *
     * Concrete configuration type for io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel
     */
    public static class RelativeDifferenceDetection {

        @Schema(type = SchemaType.STRING, required = true, example = "mean",
                description = "Relative Difference Detection filter")
        public String filter;
        @Schema(type = SchemaType.INTEGER, required = true, example = "5",
                description = "Number of most recent datapoints used for aggregating the value for comparison.")
        public Integer window;
        @Schema(type = SchemaType.NUMBER, required = true, example = "0.2",
                description = "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values.")
        public Double threshold;
        @Schema(type = SchemaType.INTEGER, required = true, example = "5",
                description = "Minimal number of preceding datapoints")
        public Integer minPrevious;
    }
    /*
    * Concrete configuration type for io.hyperfoil.tools.horreum.changedetection.FixedThresholdModel
    */
    public static class FixThreshold {
        @Schema(type = SchemaType.INTEGER, required = true, example = "95",
                description = "Threshold Value")
        Integer value;
        @Schema(type = SchemaType.BOOLEAN, required = true, example = "true",
                description = "Threshold enabled/disabled")
        Boolean enabled;
        @Schema(type = SchemaType.BOOLEAN, required = true, example = "false",
                description = "Is threshold inclusive of defined value?")
        Boolean inclusive;
    }
    public static class FixedThresholdDetection {
        @Schema(type = SchemaType.OBJECT, required = true,
                description = "Lower bound for acceptable datapoint values")
        public FixThreshold min;
        @Schema(type = SchemaType.OBJECT, required = true,
                description = "Upper bound for acceptable datapoint values")
        public FixThreshold max;
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
