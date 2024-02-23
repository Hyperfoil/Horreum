package io.hyperfoil.tools.horreum.api.data.changeDetection;

import io.hyperfoil.tools.horreum.api.data.datastore.BaseChangeDetectionConfig;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/*
 * Concrete configuration type for io.hyperfoil.tools.horreum.changedetection.RelativeDifferenceChangeDetectionModel
 */
public class RelativeDifferenceDetectionConfig  extends BaseChangeDetectionConfig {
    @Schema(type = SchemaType.STRING, required = true,  enumeration = { ChangeDetectionModelType.names.RELATIVE_DIFFERENCE } )
    public String model;
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
