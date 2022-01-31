package io.hyperfoil.tools.horreum.server;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.transaction.Status;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;

import org.jboss.logging.Logger;
import org.postgresql.util.PSQLException;

public class BaseTransactionRetryInterceptor {
   private static final Logger log = Logger.getLogger(BaseTransactionRetryInterceptor.class);
   private static final String HINT = "The transaction might succeed if retried";
   private static final int MAX_RETRIES = 10;

   @Inject
   TransactionManager tm;

   @AroundInvoke
   public Object intercept(InvocationContext ctx) throws Exception {
      for (int i = 1; ; ++i) {
         try {
            return ctx.proceed();
         } catch (Throwable t) {
            if (i > MAX_RETRIES) {
               log.error("Exceeded maximum number of retries.");
               throw t;
            }
            if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
               log.debugf("This is not the outermost invocation, propagating.");
               throw t;
            }
            Throwable ex = t;
            Set<Throwable> causes = new HashSet<>();
            if (!lookupHint(ex, causes)) {
               throw t;
            }
            Thread.yield(); // give the other transaction a bit more chance to complete
            log.infof("Retrying failed transaction, status attempt %d/%d", i, MAX_RETRIES);
            log.trace("This is the exception that caused retry: ", t);
         }
      }
   }

   private boolean lookupHint(Throwable ex, Set<Throwable> causes) {
      while (ex != null && causes.add(ex)) {
         if (ex instanceof PSQLException) {
            if (ex.getMessage().contains(HINT)) {
               return true;
            }
         }
         for (Throwable suppressed: ex.getSuppressed()) {
            if (lookupHint(suppressed, causes)) {
               return true;
            }
         }
         ex = ex.getCause();
      }
      return false;
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
