package io.hyperfoil.tools.horreum.entity;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import javax.persistence.Id;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

/**
 * This generator does not generate the ID when it is already provided and &gt; 0
 */
public class SeqIdGenerator extends SequenceStyleGenerator {
   private static final ClassValue<Function<Object, Serializable>> accessors = new ClassValue<Function<Object, Serializable>>() {
      @Override
      protected Function<Object, Serializable> computeValue(Class<?> type) {
         // Note: this is simplified, we don't cover compound IDs and such
         for (Field f : type.getFields()) {
            if (f.isAnnotationPresent(Id.class)) {
               return obj -> {
                  try {
                     return (Serializable) f.get(obj);
                  } catch (IllegalAccessException e) {
                     throw new RuntimeException(e);
                  }
               };
            }
         }
         return null;
      }
   };

   @Override
   public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
      Function<Object, Serializable> accessor = accessors.get(object.getClass());
      if (accessor != null) {
         Serializable id = accessor.apply(object);
         if (id != null && (!(id instanceof Number) || ((Number) id).intValue() > 0)) {
            return id;
         }
      }
      return super.generate(session, object);
   }
}
