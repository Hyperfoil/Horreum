package io.hyperfoil.tools.horreum.mapper;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.hyperfoil.tools.horreum.api.data.ValidationError;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;
import io.vertx.core.spi.JsonFactory;

public class ValidationErrorMapper {

    public static ValidationError fromValidationError(ValidationErrorDAO ve) {
        ValidationError dto = new ValidationError();
        dto.schemaId = ve.getSchemaId();
        dto.type = ve.error.get("type").asText();
        dto.message = ve.error.get("message").asText();
        return dto;
    }

    public static ValidationErrorDAO toValidationError(ValidationError dto) {
        ValidationErrorDAO ve = new ValidationErrorDAO();
        ve.error = JsonNodeFactory.instance.objectNode().put("type", dto.type).put("message", dto.message);

        return ve;
    }
}
