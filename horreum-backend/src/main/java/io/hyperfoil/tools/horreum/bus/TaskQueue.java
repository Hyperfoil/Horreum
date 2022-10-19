package io.hyperfoil.tools.horreum.bus;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.logging.Logger;

public class TaskQueue {
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
