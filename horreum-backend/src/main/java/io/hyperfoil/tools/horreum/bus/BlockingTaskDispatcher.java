package io.hyperfoil.tools.horreum.bus;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.hyperfoil.tools.horreum.svc.Util;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;

@Startup
@ApplicationScoped
public class BlockingTaskDispatcher {

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
                Log.error("Failed to execute blocking task", e);
            } finally {
                promise.complete();
            }
        });
    }

}

class TaskQueue {
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
                Log.debugf("This thread is going to execute tasks (%d) for test %d, lock level %d", queue.size(), testId,
                        lock.getHoldCount());
                try {
                    while (!queue.isEmpty()) {
                        Runnable task = queue.poll();
                        task.run();
                    }
                } catch (Throwable t) {
                    Log.errorf(t, "Error executing task in the queue for test %d", testId);
                } finally {
                    Log.debugf("Finished executing tasks for test %d", testId);
                    lock.unlock();
                }
            } else {
                Log.debugf("There's another thread executing the tasks (%d) for test %d", queue.size(), testId);
                return;
            }
        } while (!queue.isEmpty());
    }
}
