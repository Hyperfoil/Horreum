package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.LabelDAO;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;

import java.util.stream.Collectors;

public class LabelMapper {
    public static Label from(LabelDAO l) {
        Label dto = new Label();
        dto.id = l.id;
        dto.name = l.name;
        dto.function = l.function;
        dto.filtering = l.filtering;
        dto.metrics = l.metrics;
        dto.owner = l.owner;
        dto.access = l.access;
        dto.schemaId = l.schema.id;
        dto.extractors = l.extractors.stream().map(ExtractorMapper::from).collect(Collectors.toList());

        return dto;
    }

    public static LabelDAO to(Label dto) {
        LabelDAO l = new LabelDAO();
        l.id = dto.id;
        l.name = dto.name;
        l.function = dto.function;
        l.filtering = dto.filtering;
        l.metrics = dto.metrics;
        l.owner = dto.owner;
        l.access = dto.access;
        if(dto.schemaId > 0)
            l.schema = SchemaDAO.getEntityManager().find(SchemaDAO.class, dto.schemaId);
        l.extractors = dto.extractors.stream().map(ExtractorMapper::to).collect(Collectors.toList());

        return l;
    }

    public static Label.Value fromValue(LabelDAO.Value v) {
        Label.Value dto = new Label.Value();
        dto.labelId = v.labelId;
        dto.value = v.value;
        dto.datasetId = v.datasetId;

        return dto;
    }
}
