package io.hyperfoil.tools.horreum.exp.pasted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ProxyJackson {

    public static Object wrap(JsonNode object){
        if(object == null){
            return null;
        }else if (object.isObject()){
            return new ProxyJacksonObject((ObjectNode) object);
        }else if (object.isArray()){
            return new ProxyJacksonArray((ArrayNode) object);
        }else{
            return object;
        }
    }
}

