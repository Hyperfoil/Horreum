package io.hyperfoil.tools.horreum.svc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Handler;

public class CountDownFuture<T> extends CompletableFuture<T> implements Handler<Object> {
   private final T aggregator;
   private final AtomicInteger counter;

   public CountDownFuture(T aggregator, int parties) {
      this.aggregator = aggregator;
      this.counter = new AtomicInteger(parties);
      if (parties == 0) {
         complete(aggregator);
      }
   }

   @Override
   public void handle(Object event) {
      if (counter.decrementAndGet() == 0) {
         complete(aggregator);
      }
   }
}
