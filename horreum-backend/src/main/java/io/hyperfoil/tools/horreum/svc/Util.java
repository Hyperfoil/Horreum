package io.hyperfoil.tools.horreum.svc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.eclipse.microprofile.context.ThreadContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.NativeQuery;
import org.jboss.logging.Logger;
import org.postgresql.util.PSQLException;

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

import io.hyperfoil.tools.horreum.server.RolesInterceptor;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.context.SmallRyeContextManagerProvider;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;

public class Util {
   private static final Logger log = Logger.getLogger(Util.class);
   public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
   private static final Configuration JSONPATH_CONFIG = Configuration.builder()
         .jsonProvider(new JacksonJsonNodeJsonProvider())
         .options(Option.SUPPRESS_EXCEPTIONS,Option.DEFAULT_PATH_LEAF_TO_NULL).build();
   static final ArrayNode EMPTY_ARRAY = JsonNodeFactory.instance.arrayNode();
   static final ObjectNode EMPTY_OBJECT = JsonNodeFactory.instance.objectNode();

   public static final int MAX_TRANSACTION_RETRIES = 10;
   private static final String RETRY_HINT = "The transaction might succeed if retried";

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

   public static void doAfterCommitThrowing(TransactionManager tm, Runnable runnable) throws SystemException, RollbackException {
      tm.getTransaction().registerSynchronization(new Synchronization() {
         @Override
         public void beforeCompletion() {
         }

         @Override
         public void afterCompletion(int status) {
            try {
               if (status == Status.STATUS_COMMITTED || status == Status.STATUS_COMMITTING) {
                  runnable.run();
               }
            } catch (Throwable t) {
               log.error("Error in TX synchronization", t);
               throw t;
            }
         }
      });
   }

   public static void doAfterCommit(TransactionManager tm, Runnable runnable) {
      try {
         doAfterCommitThrowing(tm, runnable);
      } catch (RollbackException e) {
         log.debugf("Not performing %s as the transaction has been marked rollback-only", runnable);
      } catch (SystemException e) {
         log.errorf(e, "Failed to perform %s after transaction completion", runnable);
      }
   }

   static void publishLater(TransactionManager tm, final EventBus eventBus, String eventName, Object event) {
      try {
         doAfterCommitThrowing(tm, () -> eventBus.publish(eventName, event));
      } catch (RollbackException e) {
         log.debug("Not publishing the event as the transaction has been marked rollback-only");
      } catch (SystemException e) {
         log.errorf(e, "Failed to publish event %s: %s after transaction completion", eventName, event);
      }
   }

   public static JsonNode toJsonNode(String str) {
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

   public static JsonNode toJsonNode(byte[] bytes) {
      try {
         if (bytes == null) {
            return null;
         }
         return OBJECT_MAPPER.readTree(bytes);
      } catch (IOException e) {
         log.errorf(e, "Failed to parse into JSON: %s", new String(bytes, StandardCharsets.UTF_8));
         throw new RuntimeException(e);
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
                     return (long) value;
                  } else {
                     return value;
                  }
            }
         }
         return obj;
      } catch (InvalidPathException e){
         return "<invalid jsonpath>";
      }
   }

   public static <T> T withTx(TransactionManager tm, Supplier<T> supplier) {
      for (int retry = 1;; ++retry) {
         try {
            tm.begin();
            try {
               return supplier.get();
            } catch (Throwable t) {
               tm.setRollbackOnly();
               // Similar code is in BaseTransactionRetryInterceptor
               if (retry > Util.MAX_TRANSACTION_RETRIES) {
                  log.error("Exceeded maximum number of retries.");
                  throw t;
               }
               if (!lookupRetryHint(t, new HashSet<>())) {
                  throw t;
               }
               yieldAndLog(retry, t);
            } finally {
               if (tm.getStatus() == Status.STATUS_ACTIVE) {
                  tm.commit();
               } else {
                  tm.rollback();
               }
            }
         } catch (SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException | NotSupportedException ex) {
            if (!lookupRetryHint(ex, new HashSet<>())) {
               throw new RuntimeException("Failed to run transaction", ex);
            }
            yieldAndLog(retry, ex);
         }
      }
   }

   private static void yieldAndLog(int retry, Throwable t) {
      Thread.yield(); // give the other transaction a bit more chance to complete
      log.infof("Retrying failed transaction, attempt %d/%d", retry, Util.MAX_TRANSACTION_RETRIES);
      log.trace("This is the exception that caused retry: ", t);
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

   public static void executeBlocking(Vertx vertx, SecurityIdentity identity, Runnable runnable) {
      // Note that we cannot use @ThreadContextConfig in Quarkus
      Runnable wrapped = SmallRyeContextManagerProvider.getManager().newThreadContextBuilder()
            .propagated(ThreadContext.CDI).build().contextualRunnable(runnable);
      vertx.executeBlocking(promise -> {
         RolesInterceptor.setCurrentIdentity(identity);
         try {
            wrapped.run();
         } catch (Exception e) {
            log.error("Failed to execute blocking task", e);
         } finally {
            RolesInterceptor.setCurrentIdentity(null);
            promise.complete();
         }
      }, true, result -> {});
   }

   public static Uni<Void> executeBlocking(io.vertx.mutiny.core.Vertx vertx, SecurityIdentity identity, Uni<Void> uni) {
      return vertx.executeBlocking(Uni.createFrom().voidItem()
            .invoke(() -> RolesInterceptor.setCurrentIdentity(identity))
            .chain(() -> uni)
            .eventually(() -> RolesInterceptor.setCurrentIdentity(null)));
   }

   public static boolean lookupRetryHint(Throwable ex, Set<Throwable> causes) {
      while (ex != null && causes.add(ex)) {
         if (ex instanceof PSQLException) {
            if (ex.getMessage().contains(RETRY_HINT)) {
               return true;
            }
         } else if (ex instanceof OptimisticLockException) {
            return true;
         }
         for (Throwable suppressed: ex.getSuppressed()) {
            if (lookupRetryHint(suppressed, causes)) {
               return true;
            }
         }
         ex = ex.getCause();
      }
      return false;
   }

   public static JsonNode parseFingerprint(String fpString) {
      if (fpString == null || fpString.isEmpty()) {
         // any fingerprint
         return null;
      }
      return toJsonNode(URLDecoder.decode(fpString.replace("+", "%2B"), StandardCharsets.UTF_8));
   }

   static <T> void evaluateMany(List<T> input,
                                Function<T, String> function,
                                Function<T, JsonNode> object,
                                BiConsumer<T, Value> resultConsumer,
                                Consumer<T> noExecConsumer,
                                ExecutionExceptionConsumer<T> onException,
                                Consumer<String> onOutput) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("js").out(out).err(out).build()) {
         context.enter();
         try {
            for (int i = 0; i < input.size(); i++) {
               T row = input.get(i);
               String func = function.apply(row);
               if (func != null && !func.isBlank()) {
                  StringBuilder jsCode = new StringBuilder("const __obj").append(i).append(" = ").append(object.apply(row)).append(";\n");
                  jsCode.append("const __func").append(i).append(" = ").append(func).append(";\n");
                  jsCode.append("__func").append(i).append("(__obj").append(i).append(")");
                  try {
                     Value value = context.eval("js", jsCode);
                     resultConsumer.accept(row, value);
                  } catch (PolyglotException e) {
                     onException.accept(row, e, jsCode.toString());
                  }
               } else {
                  noExecConsumer.accept(row);
               }
            }
         } finally {
            if (out.size() > 0) {
               onOutput.accept(out.toString(StandardCharsets.UTF_8));
            }
            context.leave();
         }
      }
   }

   static <T> T evaluateOnce(String function, JsonNode input, Function<Value, T> processResult, BiConsumer<String, Throwable> onException, Consumer<String> onOutput) {
      StringBuilder jsCode = new StringBuilder("const __obj = ").append(input).append(";\n");
      jsCode.append("const __func = ").append(function).append(";\n");
      jsCode.append("__func(__obj)");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (Context context = Context.newBuilder("js").out(out).err(out).build()) {
         context.enter();
         try {
            Value value = context.eval("js", jsCode);
            return processResult.apply(value);
         } catch (PolyglotException e) {
            onException.accept(jsCode.toString(), e);
            return null;
         } finally {
            if (out.size() > 0) {
               onOutput.accept(out.toString());
            }
            context.leave();
         }
      }
   }

   static boolean evaluateTest(String function, JsonNode input,
                               Predicate<Value> onNotBoolean, BiConsumer<String, Throwable> onException, Consumer<String> onOutput) {
      Boolean res = evaluateOnce(makeFilter(function), input, result -> {
         if (result.isBoolean()) {
            return result.asBoolean();
         } else {
            return onNotBoolean.test(result);
         }
      }, onException, onOutput);
      return res != null && res;
   }

   public static String makeFilter(String function) {
      return "__x => (!!(" + function + ")(__x))";
   }

   static Object runQuery(EntityManager em, String query, Object... params) {
      Query q = em.createNativeQuery(query);
      for (int i = 0; i < params.length; ++i) {
         q.setParameter(i + 1, params[i]);
      }
      try {
         return q.getSingleResult();
      } catch (NoResultException e) {
         log.errorf("No results in %s with params: %s", query, Arrays.asList(params));
         throw ServiceException.notFound("No result");
      } catch (Throwable t) {
         log.errorf(t, "Query error in %s with params: %s", query, Arrays.asList(params));
         throw t;
      }
   }

   public static ScrollableResults scroll(Query query) {
      return query
            .unwrap(NativeQuery.class).setReadOnly(true).setFetchSize(100)
            .scroll(ScrollMode.FORWARD_ONLY);
   }

   public static Instant toInstant(JsonNode value) {
      if (value == null) {
         return null;
      } else if (value.isNumber()) {
         return Instant.ofEpochMilli(value.longValue());
      } else if (value.isTextual()) {
         String str = value.asText();
         //noinspection CatchMayIgnoreException
         try {
            return Instant.ofEpochMilli(Long.parseLong(str));
         } catch (NumberFormatException e) {
         }
         try {
            return ZonedDateTime.parse(str.trim(), DateTimeFormatter.ISO_DATE_TIME).toInstant();
         } catch (DateTimeParseException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   interface ExecutionExceptionConsumer<T> {
      void accept(T row, Throwable exception, String code);
   }
}
