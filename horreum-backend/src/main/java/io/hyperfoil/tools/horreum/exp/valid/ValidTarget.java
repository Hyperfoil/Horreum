package io.hyperfoil.tools.horreum.exp.valid;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE_USE, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExtractorValidator.class)
public @interface ValidTarget {
    String message() default "LabelValueExtractors must extract from labels in the same Test";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
