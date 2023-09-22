package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;

public class ValidationErrorMapper {

    public static ValidationError fromValidationError(ValidationErrorDAO ve) {
        ValidationError dto = new ValidationError();
        dto.schemaId = ve.getSchemaId();
        dto.error = ve.error;
        return dto;
    }

    public static ValidationErrorDAO toValidationError(ValidationError dto) {
        ValidationErrorDAO ve = new ValidationErrorDAO();
        ve.error = dto.error;

        return ve;
    }
}
