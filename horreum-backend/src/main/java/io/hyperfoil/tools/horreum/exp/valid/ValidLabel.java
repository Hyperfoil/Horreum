package io.hyperfoil.tools.horreum.exp.valid;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE_USE, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = LabelLoopValidator.class)
public @interface ValidLabel {
    String message() default "Label's cannot have extractor references that loop back onto themselves";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
