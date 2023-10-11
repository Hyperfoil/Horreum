package io.hyperfoil.tools.horreum.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;
import io.hyperfoil.tools.horreum.svc.Util;
import org.jboss.logging.Logger;

public class ValidationErrorMapper {

    private static final Logger log = Logger.getLogger(ValidationErrorMapper.class);


    public static ValidationError fromValidationError(ValidationErrorDAO ve) {
        ValidationError dto = new ValidationError();
        dto.schemaId = ve.getSchemaId();
        try {
            dto.error = Util.OBJECT_MAPPER.treeToValue(ve.error, ValidationError.ErrorDetails.class);
        } catch (JsonProcessingException e) {
            log.error("Could not map ValidationErrorDAO to ValidationError", e);
        }
        return dto;
    }

    public static ValidationErrorDAO toValidationError(ValidationError dto) {
        ValidationErrorDAO ve = new ValidationErrorDAO();
        ve.error = Util.OBJECT_MAPPER.valueToTree(dto.error);
        return ve;
    }
}
