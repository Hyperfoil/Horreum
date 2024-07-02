package io.hyperfoil.tools.horreum.entity;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CustomSequenceGenerator is an annotation used to specify a custom
 * sequence-based primary key generator for entity fields or methods.
 * It utilizes the {@link SeqIdGenerator} class to generate the IDs,
 * which is a custom generator that does not generate the ID when
 * it is already provided and greater than 0.
 *
 * <p>This annotation should be applied at the field or method level.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @Entity
 * public class YourEntity {
 *
 *     @Id
 *     @GeneratedValue(generator = "schemaIdGenerator")
 *     @CustomSequenceGenerator(name = "schema_id_seq", initialValue = 1, allocationSize = 1)
 *     private Long id;
 *
 *     // other fields, getters, and setters
 * }
 * }
 * </pre>
 *
 * @see SeqIdGenerator
 */
@IdGeneratorType(SeqIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface CustomSequenceGenerator {
   /**
    * (Optional) The name of the database sequence object from
    * which to obtain primary key values.
    */
   String name();

   /**
    * (Optional) The value from which the sequence object
    * is to start generating.
    */
   int initialValue() default 1;

   /**
    * (Optional) The amount to increment by when allocating
    * sequence numbers from the sequence.
    */
   int allocationSize() default 50;
}
