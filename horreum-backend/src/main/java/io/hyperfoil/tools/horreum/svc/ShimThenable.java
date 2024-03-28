package io.hyperfoil.tools.horreum.svc;

import org.graalvm.polyglot.Value;

/**
 * Provides a thenable interface so graaljs can tread the object as a Promise in async await
 */
@FunctionalInterface
public interface ShimThenable {
    void then(Value onResolve, Value onReject);
}
