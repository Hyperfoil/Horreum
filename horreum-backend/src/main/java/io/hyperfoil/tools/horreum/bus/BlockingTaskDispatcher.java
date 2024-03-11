package io.hyperfoil.tools.horreum.bus;

import io.hyperfoil.tools.horreum.svc.Util;
import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Startup
@ApplicationScoped
public class BlockingTaskDispatcher {
   private static final Logger log = Logger.getLogger(BlockingTaskDispatcher.class);
   @Inject
   Vertx vertx;

   private final ConcurrentMap<Integer, TaskQueue> taskQueues = new ConcurrentHashMap<>();

   public void executeForTest(int testId, Runnable runnable) {
      Runnable task = Util.wrapForBlockingExecution(runnable);
      vertx.executeBlocking(promise -> {
         try {
            TaskQueue queue = taskQueues.computeIfAbsent(testId, TaskQueue::new);
            queue.executeOrAdd(task);
         } catch (Exception e) {
            log.error("Failed to execute blocking task", e);
         } finally {
            promise.complete();
         }
      });
   }

}

class TaskQueue {
   private static final Logger log = Logger.getLogger(TaskQueue.class);
   private final int testId;
   private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
   private final ReentrantLock lock = new ReentrantLock();

   public TaskQueue(int testId) {
      this.testId = testId;
   }

   public void executeOrAdd(Runnable runnable) {
      queue.add(runnable);
      do {
         if (lock.tryLock()) {
            log.debugf("This thread is going to execute tasks (%d) for test %d, lock level %d", queue.size(), testId, lock.getHoldCount());
            try {
               while (!queue.isEmpty()) {
                  Runnable task = queue.poll();
                  task.run();
               }
            } catch (Throwable t) {
               log.errorf(t, "Error executing task in the queue for test %d", testId);
            } finally {
               log.debugf("Finished executing tasks for test %d", testId);
               lock.unlock();
            }
         } else {
            log.debugf("There's another thread executing the tasks (%d) for test %d", queue.size(), testId);
            return;
         }
      } while (!queue.isEmpty());
   }
}

