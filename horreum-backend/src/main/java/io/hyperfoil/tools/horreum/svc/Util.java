package io.hyperfoil.tools.horreum.svc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.graalvm.polyglot.Value;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import io.vertx.core.eventbus.EventBus;

public class Util {
   private static final Logger log = Logger.getLogger(Util.class);
   public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
   private static final Configuration JSONPATH_CONFIG = Configuration.builder()
         .jsonProvider(new JacksonJsonNodeJsonProvider())
         .options(Option.SUPPRESS_EXCEPTIONS,Option.DEFAULT_PATH_LEAF_TO_NULL).build();
   static final JsonNode EMPTY_ARRAY = JsonNodeFactory.instance.arrayNode();
   static final JsonNode EMPTY_OBJECT = JsonNodeFactory.instance.objectNode();

   static {
      OBJECT_MAPPER.registerModule(new JavaTimeModule());
   }

   static String destringify(String str) {
      if (str == null || str.isEmpty()) {
         return str;
      }
      if (str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"') {
         return str.substring(1, str.length() - 1);
      } else {
         return str;
      }
   }

   static Double toDoubleOrNull(Value value, Consumer<String> error, Consumer<String> info) {
      if (value.isNumber()) {
         double dValue = value.asDouble();
         if (Double.isFinite(dValue)) {
            return dValue;
         } else {
            error.accept("Not a finite number: " + value);
            return null;
         }
      } else if (value.isString()) {
         try {
            return Double.parseDouble(value.asString());
         } catch (NumberFormatException e) {
            error.accept("Return value " + value + " cannot be parsed into a number.");
            return null;
         }
      } else if (value.isNull()) {
         // returning null is intentional or the data does not exist, don't warn
         info.accept("Result is null, skipping.");
         return null;
      } else if ("undefined".equals(value.toString())) {
         // returning undefined is intentional, don't warn
         info.accept("Result is undefined, skipping.");
         return null;
      } else {
         error.accept("Return value " + value + " is not a number.");
         return null;
      }
   }

   static Double toDoubleOrNull(Object value) {
      if (value instanceof String) {
         String str = (String) value;
         String maybeNumber = str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"' ?
               str.substring(1, str.length() - 1) : str;
         try {
            return Double.parseDouble(maybeNumber);
         } catch (NumberFormatException e) {
            return null;
         }
      } else if (value instanceof Double) {
         return (Double) value;
      } else if (value instanceof Long) {
         return ((Long) value).doubleValue();
      } else if (value instanceof Integer) {
         return ((Integer) value).doubleValue();
      } else if (value instanceof Float) {
         return ((Float) value).doubleValue();
      } else if (value instanceof Short) {
         return ((Short) value).doubleValue();
      } else {
         return null;
      }
   }

   static void addPaging(StringBuilder sql, Integer limit, Integer page, String sort, String direction) {
      addOrderBy(sql, sort, direction);
      addLimitOffset(sql, limit, page);
   }

   static void addOrderBy(StringBuilder sql, String sort, String direction) {
      sort = sort == null || sort.trim().isEmpty() ? "start" : sort;
      direction = direction == null || direction.trim().isEmpty() ? "Ascending" : direction;
      sql.append(" ORDER BY ").append(sort);
      addDirection(sql, direction);
   }

   static void addDirection(StringBuilder sql, String direction) {
      if (direction != null) {
         sql.append("Ascending".equalsIgnoreCase(direction) ? " ASC" : " DESC");
      }
      sql.append(" NULLS LAST");
   }

   static void addLimitOffset(StringBuilder sql, Integer limit, Integer page) {
      if (limit != null && limit > 0) {
         sql.append(" limit ").append(limit);
         if (page != null && page >= 0) {
            sql.append(" offset ").append(limit * (page - 1));
         }
      }
   }

   static void publishLater(TransactionManager tm, final EventBus eventBus, String eventName, Object event) {
      try {
         tm.getTransaction().registerSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
               if (status == Status.STATUS_COMMITTED || status == Status.STATUS_COMMITTING) {
                  eventBus.publish(eventName, event);
               }
            }
         });
      } catch (RollbackException e) {
         log.debug("Not publishing the event as the transaction has been marked rollback-only");
      } catch (SystemException e) {
         log.errorf(e, "Failed to publish event %s: %s after transaction completion", eventName, event);
      }
   }

   static JsonNode toJsonNode(String str) {
      try {
         if (str == null) {
            return null;
         }
         return OBJECT_MAPPER.readTree(str);
      } catch (JsonProcessingException e) {
         log.errorf(e, "Failed to parse into JSON: %s", str);
         return null;
      }
   }

   public static LinkedHashMap<Object, JsonNode> toMap(JsonNode jsonNode) {
      LinkedHashMap<Object, JsonNode> map = new LinkedHashMap<>(jsonNode.size());
      if (jsonNode instanceof ObjectNode) {
         Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields();
         while (it.hasNext()) {
            var entry = it.next();
            map.put(entry.getKey(), entry.getValue());
         }
      } else if (jsonNode instanceof ArrayNode) {
         int index = 0;
         for (JsonNode node : jsonNode) {
            map.put(index++, node);
         }
      }
      return map;
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
         return value.asProxyObject();
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

   public static JsonNode convertMapping(Value value){
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

   public static Object findJsonPath(JsonNode input, String jsonPath){
      ReadContext ctx = JsonPath.parse(input, JSONPATH_CONFIG);
      try {
         JsonPath path = JsonPath.compile(jsonPath);
         Object obj = ctx.read(path);
         if (obj instanceof ValueNode) {
            ValueNode node = (ValueNode) obj;
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
                     return value;
                  } else {
                     return (long) value;
                  }
            }
         }
         return obj;
      } catch (InvalidPathException e){
         return "<invalid jsonpath>";
      }
   }

   public static String unwrapDoubleQuotes(String str) {
      if (str.startsWith("\"") && str.endsWith("\"")) {
         return str.substring(1, str.length() - 1);
      } else {
         return str;
      }
   }

   static <T> T withTx(TransactionManager tm, Supplier<T> supplier) {
      try {
         tm.begin();
         try {
            return supplier.get();
         } catch (Throwable t) {
            tm.setRollbackOnly();
            throw t;
         } finally {
            if (tm.getStatus() == Status.STATUS_ACTIVE) {
               tm.commit();
            } else {
               tm.rollback();
            }
         }
      } catch (SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException | NotSupportedException ex) {
         throw new RuntimeException("Failed to run transaction", ex);
      }
   }

   public static <T extends Annotation> T getAnnotation(Method method, Class<T> annotationClass) {
      T methodAnnotation = method.getAnnotation(annotationClass);
      if (methodAnnotation != null) {
         return methodAnnotation;
      } else {
         return method.getDeclaringClass().getAnnotation(annotationClass);
      }
   }

   public static String explainCauses(Throwable e) {
      StringBuilder causes = new StringBuilder();
      Set<Throwable> reported = new HashSet<>();
      while (e != null && !reported.contains(e)) {
         if (causes.length() != 0) {
            causes.append(": ");
         }
         causes.append(e.getMessage());
         reported.add(e);
         e = e.getCause();
      }
      return causes.toString();
   }
}
