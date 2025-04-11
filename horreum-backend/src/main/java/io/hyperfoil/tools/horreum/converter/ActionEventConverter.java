package io.hyperfoil.tools.horreum.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import io.hyperfoil.tools.horreum.svc.ActionEvent;

@Converter(autoApply = true)
public class ActionEventConverter implements AttributeConverter<ActionEvent, String> {

    @Override
    public String convertToDatabaseColumn(ActionEvent attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public ActionEvent convertToEntityAttribute(String dbData) {
        return dbData != null ? ActionEvent.fromValue(dbData) : null;
    }
}
