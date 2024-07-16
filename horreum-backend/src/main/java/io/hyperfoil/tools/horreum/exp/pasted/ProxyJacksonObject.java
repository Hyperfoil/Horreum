package io.hyperfoil.tools.horreum.exp.pasted;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ProxyJacksonObject implements ProxyObject {

    public static class InstanceCheck implements ProxyExecutable {

        @Override
        public Object execute(Value...args){
            if(args.length<1){
                return false;
            }else{
                Value obj = args[0];
                return obj.isProxyObject() && obj.asProxyObject() instanceof ProxyJacksonObject || ExpUtil.convert(obj) instanceof ObjectNode;
            }
        }
    }

    private ObjectNode node;
    public ProxyJacksonObject(ObjectNode node){
        this.node = node;
    }

    public ObjectNode getJsonNode(){return node;}

    @Override
    public Object getMember(String key) {
        Object rtrn = ExpUtil.convertFromJson(node.get(key));
        return rtrn;

    }

    @Override
    public Object getMemberKeys() {
        Iterator<String> iter = node.fieldNames();
        List<String> rtrn = new ArrayList<>();
        while (iter.hasNext()) {
            rtrn.add(iter.next());
        }
        return rtrn;
    }


    @Override
    public boolean hasMember(String key) {
        return node.has(key);
    }

    @Override
    public void putMember(String key, Value value) {
        node.put(key, ExpUtil.convertToJson(value));
    }

    @Override
    public boolean removeMember(String key) {
        return node.remove(key) != null;
    }


}
