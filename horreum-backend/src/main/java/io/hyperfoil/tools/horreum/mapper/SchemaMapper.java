package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.api.data.Schema;

public class SchemaMapper {
    public static Schema from(SchemaDAO s) {
        Schema dto = new Schema();
        dto.id = s.id;
        dto.name = s.name;
        dto.schema = s.schema;
        dto.description = s.description;
        dto.uri = s.uri;
        dto.owner = s.owner;
        dto.access = s.access;
        dto.token = s.token;

        return dto;
    }

    public static SchemaDAO to(Schema dto) {
        SchemaDAO s = new SchemaDAO();
        s.id = dto.id;
        s.name = dto.name;
        s.schema = dto.schema;
        s.description = dto.description;
        s.uri = dto.uri;
        s.owner = dto.owner;
        s.access = dto.access;
        s.token = dto.token;

        return s;
    }
}
