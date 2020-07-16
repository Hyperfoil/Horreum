package io.hyperfoil.tools.horreum;

import io.hyperfoil.tools.yaup.json.Json;
import io.hyperfoil.tools.yaup.json.ValueConverter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

public class JsProxyObject implements ProxyObject {


   private Json json;
   public JsProxyObject(Json json){
      this.json = json;
   }

   public Json getJson(){return json;}

   @Override
   public Object getMember(String key) {
      if (json.has(key) && json.get(key) instanceof Json){
         return new JsProxyObject(json.getJson(key));
      }
      return json.get(key);
   }

   @Override
   public Object getMemberKeys() {
      return json.keys().toArray();
   }

   @Override
   public boolean hasMember(String key) {
      return json.has(key);
   }

   @Override
   public void putMember(String key, Value value) {
      json.set(key, ValueConverter.convert(value));
   }

   @Override
   public boolean removeMember(String key) {
      boolean rtrn = json.has(key);
      json.remove(key);
      return rtrn;
   }

   @Override
   public String toString(){return json.toString(0);}
}
