package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;


@Schema(description = "a map of view component Id to the label values necessary to render the component")
public class IndexedLabelValueMap extends HashMap<String, LabelValueMap> {

    public static IndexedLabelValueMap fromObjectNode(ObjectNode node){
        Map<String, LabelValueMap> rtrn = new HashMap<>();
        if(node != null){
            node.fields().forEachRemaining(entry->{
                String entryKey = entry.getKey();
                JsonNode entryNode = entry.getValue();
                if(entryNode.isObject()){
                    LabelValueMap map = LabelValueMap.fromObjectNode((ObjectNode) entryNode);
                    rtrn.put(entryKey,map);
                }else{//this should not happen

                }
            });
        }
        return new IndexedLabelValueMap(rtrn);
    }

    public IndexedLabelValueMap(){}
    public IndexedLabelValueMap(Map<String, LabelValueMap> values){
        super();
        this.putAll(values);
    }
}