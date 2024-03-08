package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.entity.data.ExtractorDAO;

public class ExtractorMapper {

    public static Extractor from(ExtractorDAO e) {
        Extractor dto = new Extractor();
        dto.name = e.name;
        dto.isArray = e.isArray;
        dto.jsonpath = e.jsonpath;

        return dto;
    }

    public static ExtractorDAO to(Extractor dto) {
        ExtractorDAO e = new ExtractorDAO();
        e.name = dto.name;
        e.isArray = dto.isArray;
        e.jsonpath = dto.jsonpath;

        return e;
    }
}
