package io.hyperfoil.tools.horreum.exp.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.exp.pasted.ExpUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Function that runs against the values extracted for the label (currently javascript)
 */
@Entity
@Table(name = "exp_label_reducers")
public class LabelReducerDao extends PanacheEntity {


    @Column(columnDefinition = "TEXT")
    public String function;

    public LabelReducerDao(){
        this(null);
    }
    public LabelReducerDao(String function){
        this.function = function;
    }

    public boolean hasFunction(){
        return function!=null && !function.isBlank();
    }

    //based on io.hyperfoil.tools.horreum.svc.Util#evaluateWithCombinationFunction but not nearly as Byzantine
    public JsonNode evalJavascript(JsonNode input){
        if(function==null || function.isBlank()){
            return input;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = ExpUtil.createContext(out)) {
            context.enter();
            try{
                ExpUtil.setupContext(context);
                StringBuilder jsCode = new StringBuilder("const __obj").append(" = ").append(input).append(";\n");
                jsCode.append("const __func").append(" = ").append(function).append(";\n");
                jsCode.append("__func").append("(__obj").append(")");
                try {
                    Value value = context.eval("js",jsCode);
                    value = resolvePromise(value);
                    Object converted = ExpUtil.convert(value);
                    if(converted instanceof JsonNode){
                        return (JsonNode) converted;
                    } else if (converted instanceof Long){
                        return new ObjectMapper().getNodeFactory().numberNode((Long)converted);
                    } else if (converted instanceof Double){
                        return new ObjectMapper().getNodeFactory().numberNode((Double)converted);
                    } else if (converted instanceof String){
                        return new ObjectMapper().getNodeFactory().textNode((String)converted);
                    }
                } catch (PolyglotException e){
                    e.printStackTrace();
                    //it's a PoC, let's make sure we don't copy this part of the code to Horreum
                }
            } catch (IOException e) {
                e.printStackTrace();
                //it's a PoC, let's make sure we don't copy this part of the code to Horreum
            } finally {
                context.leave();
            }
        }
        System.out.println("the output of the js\n"+out);
        //if the js fails we return the input
        return input;
    }

    public static Value resolvePromise(Value value){
        if(value.getMetaObject().getMetaSimpleName().equals("Promise") && value.hasMember("then") && value.canInvokeMember("then")){
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
            if(!rejected.isEmpty()){
                value = rejected.get(0);
            }else if(resolved.size() == 1){
                value = resolved.get(0);
            }else{ //resolve.size() > 1, this doesn't happen
                //log.error("resolved promise size="+resolved.size()+", expected 1 for promise = "+value);
            }
        }
        return value;
    }
}
