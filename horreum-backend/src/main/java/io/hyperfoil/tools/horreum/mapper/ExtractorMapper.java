package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.Extractor;

public class ExtractorMapper {

    public static Extractor from(io.hyperfoil.tools.horreum.entity.data.Extractor e) {
        Extractor dto = new Extractor();
        dto.name = e.name;
        dto.array = e.array;
        dto.jsonpath = e.jsonpath;

        return dto;
    }

    public static io.hyperfoil.tools.horreum.entity.data.Extractor to(Extractor dto) {
        io.hyperfoil.tools.horreum.entity.data.Extractor e = new io.hyperfoil.tools.horreum.entity.data.Extractor();
        e.name = dto.name;
        e.array = dto.array;
        e.jsonpath = dto.jsonpath;

        return e;
    }
}
