package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Schema(type = SchemaType.OBJECT,
        description = "A map of label names to label values with the associated datasetId and runId")
public class ExportedLabelValues {
    @Schema(type = SchemaType.OBJECT, description = "a map of label name to label value for each label from the dataset",example = "{\"name" +
            "\":\"test\",\"score\":200}")
    public ObjectNode values;
    @Schema(type = SchemaType.INTEGER,description = "the run id that created the dataset",example = "101")
    public Integer runId;
    @Schema(type = SchemaType.INTEGER,description = "the unique dataset id",example = "101")
    public Integer datasetId;
    public ExportedLabelValues() {}

    public ExportedLabelValues(ObjectNode v, Integer runId, Integer datasetId) {
       this.values = v;
       this.runId = runId;
       this.datasetId = datasetId;

    }

    public static List<ExportedLabelValues> parse(List<Object[]> nodes) {
        if(nodes == null || nodes.isEmpty())
            return new ArrayList<>();
        List<ExportedLabelValues> fps = new ArrayList<>();
        nodes.forEach(objects->{
            JsonNode node = (JsonNode)objects[0];
            Integer runId = Integer.parseInt(objects[1]==null?"-1":objects[1].toString());
            Integer datasetId = Integer.parseInt(objects[2]==null?"-1":objects[2].toString());
            if(node.isObject()){
                fps.add(new ExportedLabelValues((ObjectNode) node,runId,datasetId));
            }else{
                //TODO alert that something is wrong in the db response
            }
        });
        return fps;
    }
}
