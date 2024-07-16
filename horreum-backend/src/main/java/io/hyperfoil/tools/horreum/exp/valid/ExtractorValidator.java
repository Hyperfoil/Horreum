package io.hyperfoil.tools.horreum.exp.valid;

import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

@ApplicationScoped
public class ExtractorValidator implements ConstraintValidator<ValidTarget, ExtractorDao> {
    @Override
    public void initialize(ValidTarget constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    //This is validating when added to the Label's extractors list, we need to validate before sending to persistence

    @Override
    public boolean isValid(ExtractorDao extractor, ConstraintValidatorContext constraintValidatorContext) {
        boolean rtrn = true;
        if(extractor == null){
            return false;
        }
        switch (extractor.type){
            case PATH -> {
                if(extractor.jsonpath == null || extractor.jsonpath.isBlank()){
                    rtrn = false;
                    constraintValidatorContext.buildConstraintViolationWithTemplate("jsonpath cannot be null or empty").addConstraintViolation();
                }
            }
            case VALUE -> {
                if(extractor.targetLabel == null){
                    constraintValidatorContext.buildConstraintViolationWithTemplate("label value extractor needs a valid target label").addConstraintViolation();
                    rtrn = false;
                }else if(!extractor.parent.parent.equals(extractor.targetLabel.parent)){
                    constraintValidatorContext.buildConstraintViolationWithTemplate("label value extractor must extract from a label in the same test").addConstraintViolation();
                }
            }
            case METADATA -> {
                if(extractor.column_name == null || !Arrays.asList("metadata").contains(extractor.column_name)){
                    constraintValidatorContext.buildConstraintViolationWithTemplate("horreum metadata extractor needs a valid target field but target was "+extractor.column_name).addConstraintViolation();
                    rtrn = false;
                }
            }
        }

        return rtrn;
    }
}
