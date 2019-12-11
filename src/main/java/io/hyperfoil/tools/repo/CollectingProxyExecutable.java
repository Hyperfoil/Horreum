package io.hyperfoil.tools.repo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RegisterForReflection
public class CollectingProxyExecutable implements ProxyExecutable {

   private List<Value> collected = new ArrayList<>();

   @Override
   public Object execute(Value... arguments) {
      System.out.println("CPE.execute("+arguments.length+")");
      collected.addAll(Arrays.asList(arguments));
      return arguments;
   }

   public List<Value> getCollected(){return collected;}
   public int size(){return collected.size();}
   public Value get(int index){
      return index >= 0 && index < size() ? collected.get(index) : null;
   }
}
