package io.hyperfoil.tools.horreum.entity;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Properties;
import java.util.function.Function;

import jakarta.persistence.Id;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * Custom sequence ID generator that extends {@link SequenceStyleGenerator}.
 * This generator does not generate the ID when it is already provided and greater than 0.
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

    private final String sequenceName;
    private final Integer allocationSize;
    private final Integer initialValue;

    public SeqIdGenerator(CustomSequenceGenerator config, Member annotatedMember, CustomIdGeneratorCreationContext context) {
        sequenceName = config.name();
        allocationSize = config.allocationSize();
        initialValue = config.initialValue();
    }

    @Override
    public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) throws MappingException {
        parameters.put(SequenceStyleGenerator.SEQUENCE_PARAM, sequenceName);
        parameters.put(SequenceStyleGenerator.INCREMENT_PARAM, allocationSize);
        parameters.put(SequenceStyleGenerator.INITIAL_PARAM, initialValue);
        super.configure(type, parameters, serviceRegistry);
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
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
