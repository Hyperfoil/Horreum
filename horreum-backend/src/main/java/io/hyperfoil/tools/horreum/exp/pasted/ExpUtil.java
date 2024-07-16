package io.hyperfoil.tools.horreum.exp.pasted;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;

import java.io.IOException;
import java.io.OutputStream;

public class ExpUtil {


    public static Context createContext(OutputStream out){
        return Context.newBuilder("js")
                .engine(Engine.newBuilder("js").option("engine.WarnInterpreterOnly","false").build())
                .allowExperimentalOptions(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.global-property","true")
                .out(out)
                .err(out)
                .build();
    }
    public static void setupContext(Context context) throws IOException {
        Source fetchSource = Source.newBuilder("js",
                "fetch = async (url,options)=>new Promise(new (Java.type('io.hyperfoil.tools.exp.horreum.pasted.ShimFetch'))(url,options));","fakeFetch").build();
        context.eval(fetchSource);
        context.eval("js","atob = (str)=>Java.type('io.hyperfoil.tools.exp.horreum.pasted.ShimFetch').atob(str)");
        context.eval("js","btoa = (str)=>Java.type('io.hyperfoil.tools.exp.horreum.pasted.ShimFetch').btoa(str)");
        context.getBindings("js").putMember("isInstanceLike", new ProxyJacksonObject.InstanceCheck());
        context.eval("js",
                "Object.defineProperty(Object,Symbol.hasInstance, {\n" +
                        "  value: function myinstanceof(obj) {\n" +
                        "    return isInstanceLike(obj);\n" +
                        "  }\n" +
                        "});");
    }

    public static ObjectNode convertMapping(Value value){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        for (String key : value.getMemberKeys()){
            Value element = value.getMember(key);
            if (element == null || element.isNull()) {
                json.set(key, JsonNodeFactory.instance.nullNode());
            } else if (element.isBoolean()) {
                json.set(key, JsonNodeFactory.instance.booleanNode(element.asBoolean()));
            } else if (element.isNumber()) {
                double v = element.asDouble();
                if (v == Math.rint(v)) {
                    json.set(key, JsonNodeFactory.instance.numberNode(element.asLong()));
                } else {
                    json.set(key, JsonNodeFactory.instance.numberNode(v));
                }
            } else if (element.isString()) {
                json.set(key, JsonNodeFactory.instance.textNode(element.asString()));
            } else if (element.hasArrayElements()) {
                json.set(key, convertArray(element));
            } else if (element.hasMembers()) {
                json.set(key, convertMapping(element));
            } else {
                json.set(key, JsonNodeFactory.instance.textNode(element.toString()));
            }
        }
        return json;
    }
    public static ArrayNode convertArray(Value value){
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        for(int i = 0; i < value.getArraySize(); i++){
            Value element = value.getArrayElement(i);
            if (element == null || element.isNull()) {
                json.addNull();
            } else if (element.isBoolean()) {
                json.add(element.asBoolean());
            } else if (element.isNumber()) {
                double v = element.asDouble();
                if (v == Math.rint(v)) {
                    json.add(element.asLong());
                } else {
                    json.add(v);
                }
            } else if (element.isString()) {
                json.add(element.asString());
            } else if (element.hasArrayElements()) {
                json.add(convertArray(element));
            } else if (element.hasMembers()) {
                json.add(convertMapping(element));
            } else {
                json.add(element.toString());
            }
        }
        return json;
    }

    public static Object convert(Value value) {
        if (value == null) {
            return null;
        } else if (value.isNull()) {
            // Value api cannot differentiate null and undefined from javascript
            if (value.toString().contains("undefined")) {
                return ""; //no return is the same as returning a missing key from a ProxyObject?
            } else {
                return null;
            }
        } else if (value.isProxyObject()) {
            Proxy p = value.asProxyObject();
            if (p instanceof ProxyJacksonArray){
                return ((ProxyJacksonArray)p).getJsonNode();
            } else if (p instanceof ProxyJacksonObject){
                return ((ProxyJacksonObject)p).getJsonNode();
            } else {
                return p;
            }
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isNumber()) {
            double v = value.asDouble();
            if (v == Math.rint(v)) {
                return (long) v;
            } else {
                return v;
            }
        } else if (value.isString()) {
            return value.asString();
        } else if (value.hasArrayElements()) {
            return convertArray(value);
        } else if (value.canExecute()) {
            return value.toString();
        } else if (value.hasMembers()) {
            return convertMapping(value);
        } else {
            //TODO log error wtf is Value?
            return "";
        }
    }
    public static Object convertFromJson(JsonNode node){
        switch (node.getNodeType()) {
            case BINARY:
            case STRING:
                return node.asText();
            case BOOLEAN:
                return node.asBoolean();
            case MISSING:
            case NULL:
                return null;
            case NUMBER:
                double value = node.asDouble();
                if (value == Math.rint(value)) {
                    return (long) value;
                } else {
                    return value;
                }
            case OBJECT:
                return (ObjectNode)node;
            case ARRAY:
                return (ArrayNode)node;
            default:
                return node;
        }
    }

    public static JsonNode convertToJson(Value value) {
        if (value == null || value.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        } else if (value.isProxyObject()) {
            return value.asProxyObject();
        } else if (value.isBoolean()) {
            return JsonNodeFactory.instance.booleanNode(value.asBoolean());
        } else if (value.isNumber()) {
            double v = value.asDouble();
            if (v == Math.rint(v)) {
                return JsonNodeFactory.instance.numberNode((long) v);
            } else {
                return JsonNodeFactory.instance.numberNode(v);
            }
        } else if (value.isString()) {
            return JsonNodeFactory.instance.textNode(value.asString());
        } else if (value.hasArrayElements()) {
            return convertArray(value);
        } else if (value.canExecute()) {
            return JsonNodeFactory.instance.textNode(value.toString());
        } else if (value.hasMembers()) {
            return convertMapping(value);
        } else {
            return JsonNodeFactory.instance.textNode(value.toString());
        }
    }
}
