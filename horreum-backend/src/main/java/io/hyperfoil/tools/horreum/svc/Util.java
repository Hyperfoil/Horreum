package io.hyperfoil.tools.horreum.svc;

import java.util.function.Consumer;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.graalvm.polyglot.Value;
import org.jboss.logging.Logger;

import io.vertx.core.eventbus.EventBus;

class Util {
   private static final Logger log = Logger.getLogger(Util.class);

   static String destringify(String str) {
      if (str == null || str.isEmpty()) {
         return str;
      }
      if (str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"') {
         return str.substring(1, str.length() - 1);
      } else {
         return str;
      }
   }

   static Double toDoubleOrNull(Value value, Consumer<String> error, Consumer<String> info) {
      if (value.isNumber()) {
         double dValue = value.asDouble();
         if (Double.isFinite(dValue)) {
            return dValue;
         } else {
            error.accept("Not a finite number: " + value);
            return null;
         }
      } else if (value.isString()) {
         try {
            return Double.parseDouble(value.asString());
         } catch (NumberFormatException e) {
            error.accept("Return value " + value + " cannot be parsed into a number.");
            return null;
         }
      } else if (value.isNull()) {
         // returning null is intentional or the data does not exist, don't warn
         info.accept("Result is null, skipping.");
         return null;
      } else if ("undefined".equals(value.toString())) {
         // returning undefined is intentional, don't warn
         info.accept("Result is undefined, skipping.");
         return null;
      } else {
         error.accept("Return value " + value + " is not a number.");
         return null;
      }
   }

   static Double toDoubleOrNull(Object value) {
      if (value instanceof String) {
         String str = (String) value;
         String maybeNumber = str.charAt(0) == '"' && str.charAt(str.length() - 1) == '"' ?
               str.substring(1, str.length() - 1) : str;
         try {
            return Double.parseDouble(maybeNumber);
         } catch (NumberFormatException e) {
            return null;
         }
      } else if (value instanceof Double) {
         return (Double) value;
      } else if (value instanceof Long) {
         return ((Long) value).doubleValue();
      } else if (value instanceof Integer) {
         return ((Integer) value).doubleValue();
      } else if (value instanceof Float) {
         return ((Float) value).doubleValue();
      } else if (value instanceof Short) {
         return ((Short) value).doubleValue();
      } else {
         return null;
      }
   }

   static void addPaging(StringBuilder sql, Integer limit, Integer page, String sort, String direction) {
      addOrderBy(sql, sort, direction);
      addLimitOffset(sql, limit, page);
   }

   static void addOrderBy(StringBuilder sql, String sort, String direction) {
      sort = sort == null || sort.trim().isEmpty() ? "start" : sort;
      direction = direction == null || direction.trim().isEmpty() ? "Ascending" : direction;
      sql.append(" ORDER BY ").append(sort);
      addDirection(sql, direction);
   }

   static void addDirection(StringBuilder sql, String direction) {
      if (direction != null) {
         sql.append("Ascending".equalsIgnoreCase(direction) ? " ASC" : " DESC");
      }
      sql.append(" NULLS LAST");
   }

   static void addLimitOffset(StringBuilder sql, Integer limit, Integer page) {
      if (limit != null && limit > 0) {
         sql.append(" limit ").append(limit);
         if (page != null && page >= 0) {
            sql.append(" offset ").append(limit * (page - 1));
         }
      }
   }

   static void publishLater(TransactionManager tm, final EventBus eventBus, String eventName, Object event) {
      try {
         tm.getTransaction().registerSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
               if (status == Status.STATUS_COMMITTED || status == Status.STATUS_COMMITTING) {
                  eventBus.publish(eventName, event);
               }
            }
         });
      } catch (RollbackException e) {
         log.debug("Not publishing the event as the transaction has been marked rollback-only");
      } catch (SystemException e) {
         log.errorf(e, "Failed to publish event %s: %s after transaction completion", eventName, event);
      }
   }
}
