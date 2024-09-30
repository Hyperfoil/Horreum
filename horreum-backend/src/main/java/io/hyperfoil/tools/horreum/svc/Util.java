package io.hyperfoil.tools.horreum.svc;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Qualifier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Query;
import jakarta.transaction.*;

import org.eclipse.microprofile.context.ThreadContext;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.hibernate.query.NativeQuery;
import org.jboss.logging.Logger;
import org.postgresql.util.PSQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import io.hyperfoil.tools.horreum.api.SortDirection;
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
            .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL).build();
    static final ArrayNode EMPTY_ARRAY = JsonNodeFactory.instance.arrayNode();
    static final ObjectNode EMPTY_OBJECT = JsonNodeFactory.instance.objectNode();

    public static final int MAX_TRANSACTION_RETRIES = 10;
    private static final String RETRY_HINT = "The transaction might succeed if retried";

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Qualifier
    @Retention(RUNTIME)
    @Target({ METHOD, FIELD, PARAMETER, TYPE })
    public @interface FailUnknownProperties {
    }

    @Produces
    @FailUnknownProperties
    @ApplicationScoped
    public ObjectMapper producerObjectMapper() {
        BeanManager beanManager = CDI.current().getBeanManager();

        Bean<ObjectMapper> bean = (Bean<ObjectMapper>) beanManager.resolve(beanManager.getBeans(ObjectMapper.class));
        ObjectMapper objectMapper = beanManager.getContext(bean.getScope()).get(bean,
                beanManager.createCreationalContext(bean));

        ObjectMapper customMapper = objectMapper.copy();
        customMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return customMapper;
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
            String maybeNumber = str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"'
                    ? str.substring(1, str.length() - 1)
                    : str;
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

    static void addPaging(StringBuilder sql, Integer limit, Integer page, String sort, SortDirection direction) {
        addOrderBy(sql, sort, direction);
        addLimitOffset(sql, limit, page);
    }

    static void addOrderBy(StringBuilder sql, String sort, SortDirection direction) {
        sort = sort == null || sort.trim().isEmpty() ? "start" : sort;
        direction = direction == null ? SortDirection.Descending : direction;
        sql.append(" ORDER BY ").append(sort);
        addDirection(sql, direction);
    }

    static void addDirection(StringBuilder sql, SortDirection direction) {
        if (direction != null) {
            sql.append("Ascending".equalsIgnoreCase(direction.toString()) ? " ASC" : " DESC");
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

    public static void doAfterCommitThrowing(TransactionManager tm, Runnable runnable)
            throws SystemException, RollbackException {
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

    public static Object convertFromJson(JsonNode node) {
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
                return (ObjectNode) node;
            case ARRAY:
                return (ArrayNode) node;
            default:
                return node;
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
            Proxy p = value.asProxyObject();
            if (p instanceof ProxyJacksonArray) {
                return ((ProxyJacksonArray) p).getJsonNode();
            } else if (p instanceof ProxyJacksonObject) {
                return ((ProxyJacksonObject) p).getJsonNode();
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

    public static ArrayNode convertArray(Value value) {
        ArrayNode json = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < value.getArraySize(); i++) {
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

    public static ObjectNode convertMapping(Value value) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        for (String key : value.getMemberKeys()) {
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

    public static Object findJsonPath(JsonNode input, String jsonPath) {
        ReadContext ctx = JsonPath.parse(input, JSONPATH_CONFIG);
        try {
            JsonPath path = JsonPath.compile(jsonPath);
            Object obj = ctx.read(path);
            if (obj instanceof ArrayNode) {
                if (((ArrayNode) obj).size() == 1) {
                    obj = ((ArrayNode) obj).get(0);
                }
            }
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
        } catch (InvalidPathException e) {
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
            } catch (SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException
                    | NotSupportedException ex) {
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

    public static void executeBlocking(Vertx vertx, Runnable runnable) {
        Runnable wrapped = wrapForBlockingExecution(runnable);
        vertx.executeBlocking(promise -> {
            try {
                wrapped.run();
            } catch (Exception e) {
                log.error("Failed to execute blocking task", e);
            } finally {
                promise.complete();
            }
        }, result -> {
        });
    }

    public static Runnable wrapForBlockingExecution(Runnable runnable) {
        // CDI needs to be propagated - without that the interceptors wouldn't run.
        // Without thread context propagation we would get an exception in Run.findById, though the interceptors would be invoked correctly.
        Runnable withThreadContext = SmallRyeContextManagerProvider.getManager().newThreadContextBuilder()
                .propagated(ThreadContext.CDI).build().contextualRunnable(runnable);
        return () -> {
            // Note: this won't help with accessing the injected security identity
            RolesInterceptor.setCurrentIdentity(CachedSecurityIdentity.ANONYMOUS);
            try {
                withThreadContext.run();
            } finally {
                RolesInterceptor.setCurrentIdentity(null);
            }
        };
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
            for (Throwable suppressed : ex.getSuppressed()) {
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

    /*
     * Evaluates a List of Objects, executing Javascript Combination Functions, if defined.
     * Callbacks for JS evaluation exceptions and output logging allow for custom error handling
     */
    static <T> void evaluateWithCombinationFunction(List<T> inputData,
            Function<T, String> jsCombinationFunction,
            Function<T, JsonNode> evaluationInputObject,
            BiConsumer<T, Value> jsFuncResultConsumer,
            Consumer<T> nonFuncResultConsumer,
            ExecutionExceptionConsumer<T> onJsEvaluationException,
            Consumer<String> jsOutputConsumer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int i = 0; i < inputData.size(); i++) {
            T element = inputData.get(i);
            String jsFuncBody = jsCombinationFunction.apply(element);
            if (jsFuncBody != null && !jsFuncBody.isBlank()) {
                try (org.graalvm.polyglot.Context context = createContext(out)) {
                    context.enter();
                    try {
                        setupContext(context);
                        StringBuilder jsCode = new StringBuilder("const __obj").append(i).append(" = ")
                                .append(evaluationInputObject.apply(element)).append(";\n");
                        jsCode.append("const __func").append(i).append(" = ").append(jsFuncBody).append(";\n");
                        jsCode.append("__func").append(i).append("(__obj").append(i).append(")");
                        try {
                            Value value = context.eval("js", jsCode);
                            value = resolvePromise(value);
                            jsFuncResultConsumer.accept(element, value);
                        } catch (PolyglotException e) {
                            onJsEvaluationException.accept(element, e, jsCode.toString());
                        }
                    } catch (IOException e) {
                        onJsEvaluationException.accept(null, e, "<init>");
                    } finally {
                        context.leave();
                    }
                }
            } else {
                nonFuncResultConsumer.accept(element);
            }
        }
        if (out.size() > 0) {
            jsOutputConsumer.accept(out.toString(StandardCharsets.UTF_8));
        }
    }

    private static Context createContext(OutputStream out) {
        return Context.newBuilder("js")
                .engine(Engine.newBuilder()
                        .option("engine.WarnInterpreterOnly", "false")
                        .build())
                .allowExperimentalOptions(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.global-property", "true")
                .out(out)
                .err(out)
                .build();
    }

    private static void setupContext(Context context) throws IOException {
        context.getBindings("js").putMember("isInstanceLike", new ProxyJacksonObject.InstanceCheck());
        context.eval("js",
                "Object.defineProperty(Object,Symbol.hasInstance, {\n" +
                        "  value: function myinstanceof(obj) {\n" +
                        "    return isInstanceLike(obj);\n" +
                        "  }\n" +
                        "});");
    }

    public static Value resolvePromise(Value value) {
        if (value.getMetaObject().getMetaSimpleName().equals("Promise") && value.hasMember("then")
                && value.canInvokeMember("then")) {
            List<Value> resolved = new ArrayList<>();
            List<Value> rejected = new ArrayList<>();
            Object invokeRtrn = value.invokeMember("then", new ProxyExecutable() {
                @Override
                public Object execute(Value... arguments) {
                    resolved.addAll(Arrays.asList(arguments));
                    return arguments;
                }
            }, new ProxyExecutable() {
                @Override
                public Object execute(Value... arguments) {
                    rejected.addAll(Arrays.asList(arguments));
                    return arguments;
                }
            });
            if (!rejected.isEmpty()) {
                value = rejected.get(0);
            } else if (resolved.size() == 1) {
                value = resolved.get(0);
            } else { //resolve.size() > 1, this doesn't happen
                log.error("resolved promise size=" + resolved.size() + ", expected 1 for promise = " + value);
            }
        }
        return value;
    }

    //I SWEAR IF I FIND ANOTHER PLACE THAT PERFORMS THE SAME CALCULATION I WILL BUY MORE SCREWDRIVERS
    static <T> T evaluateOnce(String function, JsonNode input, Function<Value, T> processResult,
            BiConsumer<String, Throwable> onException, Consumer<String> onOutput) {
        StringBuilder jsCode = new StringBuilder("const __obj = ").append(input).append(";\n");
        jsCode.append("const __func = ").append(function).append(";\n");
        jsCode.append("__func(__obj)");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = createContext(out)) {
            context.enter();
            try {
                setupContext(context);
                Value value = context.eval("js", jsCode);
                value = resolvePromise(value);
                //end of the sin
                return processResult.apply(value);
            } catch (PolyglotException e) {
                onException.accept(jsCode.toString(), e);
                return null;
            } catch (IOException e) {
                onException.accept(jsCode.toString(), e);
            } finally {
                if (out.size() > 0) {
                    onOutput.accept(out.toString());
                }
                context.leave();
            }
        }
        return null;
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

    static public Object runQuery(EntityManager em, String query, Object... params) {
        return runQuery(em, Object.class, query, params);
    }

    static <T> T runQuery(EntityManager em, Class<T> klass, String query, Object... params) {
        Query q;
        q = klass.equals(Object.class) ? em.createNativeQuery(query) : em.createNativeQuery(query, klass);
        for (int i = 0; i < params.length; ++i) {
            q.setParameter(i + 1, params[i]);
        }
        try {
            return (T) q.getSingleResult();
        } catch (NoResultException e) {
            log.errorf("No results in %s with params: %s", query, Arrays.asList(params));
            throw ServiceException.notFound("No result");
        } catch (Throwable t) {
            log.errorf(t, "Query error in %s with params: %s", query, Arrays.asList(params));
            throw t;
        }
    }

    public static Instant toInstant(Object time) {
        if (time == null) {
            return null;
        } else if (time instanceof Instant) {
            return (Instant) time; //crazier things happen
        } else if (time instanceof JsonNode) {
            JsonNode value = (JsonNode) time;
            if (value.isNumber()) {
                return Instant.ofEpochMilli(value.longValue());
            } else if (value.isTextual()) {
                time = value.asText(); //allow next set of ifs to check the value
            }
        }
        if (time instanceof Number) {
            return Instant.ofEpochMilli(((Number) time).longValue());
        } else {
            String str = time.toString().trim();
            if (str.isBlank()) {
                return null;
            }
            if (str.matches("\\d+")) {
                try {
                    return Instant.ofEpochMilli(Long.parseLong((String) time));
                } catch (NumberFormatException e) {
                    // noop
                }
            }
            //ISO_DATE we add midnight zulu offset
            if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
                str = str + "T00:00:00Z";
            }
            //ISO_LOCAL_DATE_TIME add zulu offset
            if (str.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                str = str + "Z";
            }
            //ISO_DATE_TIME
            try {
                return ZonedDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME).toInstant();
            } catch (DateTimeParseException e) {
                log.debug("failed to convert " + time + " to timestamp using " + str);
            }
        }
        return null;//nothing matched
    }

    /**
     * used to check if an input can be cast to a target type in the db and return any error messages
     * Will return a row of all nulls if the input can be cast to the target type.
     */
    //tried pg_input_is_valid but it just returns boolean, no messages
    private static final String CHECK_CAST = "select * from pg_input_error_info(:input,:target)";

    public static record CheckResult(boolean ok, String message, String detail, String hint) {
    }

    /**
     * returns true (in the CheckResult) if the input can be cast to the target type in psql, otherwise it is false and details
     * are included
     *
     * @param input
     * @param target
     * @param em
     * @return
     */
    public static CheckResult castCheck(String input, String target, EntityManager em) {
        List<Object[]> results = null;
        // skip db query if the input is null or blank
        if (input != null && !input.isBlank()) {
            results = em.createNativeQuery(CHECK_CAST).setParameter("input", input).setParameter("target", target)
                    .unwrap(NativeQuery.class)
                    .addScalar("message", String.class)
                    .addScalar("detail", String.class)
                    .addScalar("hint", String.class)
                    .addScalar("sql_error_code", String.class)
                    .getResultList();
        }

        if (results == null) {
            return new CheckResult(
                    false,
                    "",
                    "",
                    "");
        }

        // no results or null result row or no message means it passed. no result and 0 length result should not happen but being defensive
        return results.isEmpty() || results.get(0).length == 0 || results.get(0)[0] == null ? new CheckResult(true, "", "", "")
                : new CheckResult(
                        false,
                        results.get(0)[0] == null ? "" : results.get(0)[0].toString(),
                        results.get(0)[1] == null ? "" : results.get(0)[1].toString(),
                        results.get(0)[2] == null ? "" : results.get(0)[2].toString());
    }

    /**
     * returns null if no filtering, otherwise returns an object for filtering
     *
     * @param input filter string
     * @return JsonNode, original string or null
     */
    public static Object getFilterObject(String input) {
        if (input == null || input.isBlank()) {
            // not a valid filter
            return null;
        }
        JsonNode filterJson = null;
        try {
            filterJson = new ObjectMapper().readTree(input);
        } catch (JsonProcessingException e) {
            // TODO what to do with this error
        }
        if (filterJson != null && filterJson.getNodeType() == JsonNodeType.OBJECT) {
            return filterJson;
        } else {
            // TODO validate the jsonpath?
            return input;
        }
    }

    interface ExecutionExceptionConsumer<T> {
        void accept(T row, Throwable exception, String code);
    }

    public static void registerTxSynchronization(TransactionManager tm, IntConsumer consumer) {
        try {
            if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
                Transaction tx = tm.getTransaction();
                tx.registerSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {
                        //do nothing
                    }

                    @Override
                    public void afterCompletion(int status) {

                        try {
                            consumer.accept(status);
                        } catch (Exception e) {
                            log.errorf("Tx Synchronization callback failed: %s", e.getMessage());
                        }
                    }
                });
            } else {
                consumer.accept(0);
            }
        } catch (SystemException | RollbackException e) {
            log.errorf("Error occurred in transaction: %s", e.getMessage());
            //         throw new RuntimeException(e);
            consumer.accept(0);
        } catch (Exception e) {
            log.errorf("Error occurred processing consumer: %s", e.getMessage());
        }

    }

}
