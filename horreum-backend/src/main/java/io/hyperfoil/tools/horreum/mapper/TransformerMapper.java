package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.TransformerDAO;
import io.hyperfoil.tools.horreum.api.data.Transformer;

import java.util.Collections;
import java.util.stream.Collectors;

public class TransformerMapper {
    public static Transformer from(TransformerDAO t) {
        Transformer dto = new Transformer();
        dto.id = t.id;
        dto.name = t.name;
        dto.description = t.description;
        dto.function = t.function;
        dto.schemaId = t.getSchemaId();
        dto.schemaName = t.getSchemaName();
        dto.schemaUri = t.getSchemaUri();
        dto.owner = t.owner;
        dto.access = t.access;
        dto.targetSchemaUri = t.targetSchemaUri;
        if(t.extractors != null)
            dto.extractors = t.extractors.stream().map(ExtractorMapper::from).collect(Collectors.toList());
        else
            dto.extractors = Collections.emptyList();

        return dto;
    }

    public static TransformerDAO to(Transformer dto) {
        TransformerDAO t = new TransformerDAO();
        t.id = dto.id;
        t.name = dto.name;
        t.description = dto.description;
        t.function = dto.function;
        t.targetSchemaUri = dto.targetSchemaUri;
        if(dto.schemaId != null && dto.schemaId > 0)
            t.setSchemaId(dto.schemaId);
        t.owner = dto.owner;
        t.access = dto.access;
        if(dto.extractors != null)
            t.extractors = dto.extractors.stream().map(ExtractorMapper::to).collect(Collectors.toList());
        else
            t.extractors = Collections.emptyList();

        return t;
    }
}
