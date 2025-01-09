package io.hyperfoil.tools.horreum.exp.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.tools.horreum.exp.pasted.ExpUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Function that runs against the values extracted for the label (currently javascript)
 */
@Entity
@Table(name = "exp_label_reducers")
public class LabelReducerDao extends PanacheEntity {

    @Column(columnDefinition = "TEXT")
    public String function;

    public LabelReducerDao() {
        this(null);
    }

    public LabelReducerDao(String function) {
        this.function = function;
    }

    public boolean hasFunction() {
        return function != null && !function.isBlank();
    }

    public static record EvalResult(JsonNode result, String message) {
        public EvalResult(JsonNode result) {
            this(result, null);
        }

        public boolean hasError() {
            return message != null && !message.isBlank();
        }
    }

    //based on io.hyperfoil.tools.horreum.svc.Util#evaluateWithCombinationFunction but not nearly as Byzantine
    public EvalResult evalJavascript(JsonNode input) {
        JsonNode rtrnNode = input;
        String message = null;
        if (function == null || function.isBlank()) {
            return new EvalResult(input);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = ExpUtil.createContext(out)) {
            context.enter();
            try {
                ExpUtil.setupContext(context);
                StringBuilder jsCode = new StringBuilder("const __obj").append(" = ").append(input).append(";\n");
                jsCode.append("const __func").append(" = ").append(function).append(";\n");
                jsCode.append("__func").append("(__obj").append(")");
                try {
                    Value value = context.eval("js", jsCode);
                    value = resolvePromise(value);
                    Object converted = ExpUtil.convert(value);
                    if (converted instanceof JsonNode) {
                        rtrnNode = (JsonNode) converted;
                    } else if (converted instanceof Long) {
                        rtrnNode = new ObjectMapper().getNodeFactory().numberNode((Long) converted);
                    } else if (converted instanceof Double) {
                        rtrnNode = new ObjectMapper().getNodeFactory().numberNode((Double) converted);
                    } else if (converted instanceof String) {
                        rtrnNode = new ObjectMapper().getNodeFactory().textNode((String) converted);
                    }
                } catch (PolyglotException e) {
                    e.printStackTrace();
                    SourceSection sourceSection = e.getSourceLocation();
                    if (sourceSection != null) {
                        int startLine = sourceSection.getStartLine();
                        int endLine = sourceSection.getEndLine();
                        int startColumn = sourceSection.getStartColumn();
                        int endColumn = sourceSection.getEndColumn();
                        if (startColumn == endColumn && startColumn == 1) {//1,1 usually meas we check the stack
                            Iterable<PolyglotException.StackFrame> iterator = e.getPolyglotStackTrace();
                            Iterator<PolyglotException.StackFrame> iter = iterator.iterator();
                            if (iter.hasNext()) {
                                PolyglotException.StackFrame sf = iter.next();
                                SourceSection ss = sf.getSourceLocation();
                                if (ss != null) {
                                    startLine = ss.getStartLine();
                                    endLine = ss.getEndLine();
                                    startColumn = ss.getStartColumn();
                                    endColumn = ss.getEndColumn();
                                }
                            }
                        }

                        String split[] = function.split(System.lineSeparator());
                        if (split.length >= startLine) {
                            message = split[startLine - 1];
                            message += System.lineSeparator();
                            if (startColumn == endColumn) {
                                message += String.format("%" + startColumn + "s", "^");
                            } else {
                                message += String.format("%" + startColumn + "s", "^")
                                        + String.format("%" + (endColumn - startColumn) + "s", "^").replaceAll(" ", "-");
                            }
                        }

                    } else {
                        message = e.getMessage();
                    }
                    //it's a PoC, let's make sure we don't copy this part of the code to Horreum
                }
            } catch (IOException e) {
                e.printStackTrace();
                message = e.getMessage();
                //it's a PoC, let's make sure we don't copy this part of the code to Horreum
            } finally {
                context.leave();
            }
        }
        System.out.println("the output of the js\n" + out);
        if (message != null && !message.isBlank()) {
            System.out.println("Error for reducer " + this.id);
        }
        return new EvalResult(rtrnNode, message);
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
                     //log.message("resolved promise size="+resolved.size()+", expected 1 for promise = "+value);
            }
        }
        return value;
    }
}
