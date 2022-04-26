package io.hyperfoil.tools.horreum.server;

import java.util.HashSet;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.transaction.Status;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;

import io.hyperfoil.tools.horreum.svc.Util;

public class BaseTransactionRetryInterceptor {
   private static final Logger log = Logger.getLogger(BaseTransactionRetryInterceptor.class);

   @Inject
   TransactionManager tm;

   @AroundInvoke
   public Object intercept(InvocationContext ctx) throws Exception {
      for (int i = 1; ; ++i) {
         try {
            return ctx.proceed();
         } catch (Throwable t) {
            if (i > Util.MAX_TRANSACTION_RETRIES) {
               log.error("Exceeded maximum number of retries.");
               throw t;
            }
            if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
               log.debugf("This is not the outermost invocation, propagating.");
               throw t;
            }
            if (!Util.lookupRetryHint(t, new HashSet<>())) {
               throw t;
            }
            Thread.yield(); // give the other transaction a bit more chance to complete
            log.infof("Retrying failed transaction, attempt %d/%d", i, Util.MAX_TRANSACTION_RETRIES);
            log.trace("This is the exception that caused retry: ", t);
         }
      }
   }

   @Interceptor
   @Priority(Interceptor.Priority.PLATFORM_BEFORE + 199)
   @Transactional(Transactional.TxType.REQUIRED)
   public static class RequiredTransactionRetryInterceptor extends BaseTransactionRetryInterceptor {
   }

   @Interceptor
   @Priority(Interceptor.Priority.PLATFORM_BEFORE + 199)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   public static class RequiresNewTransactionRetryInterceptor extends BaseTransactionRetryInterceptor {
   }
}
