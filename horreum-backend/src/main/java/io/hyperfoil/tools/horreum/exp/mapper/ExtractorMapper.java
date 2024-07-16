package io.hyperfoil.tools.horreum.exp.mapper;

import io.hyperfoil.tools.horreum.api.exp.data.Extractor;
import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;


public class ExtractorMapper {
    public static Extractor from(ExtractorDao l) {
        if(l == null)
            return  null;
        Extractor dto = new Extractor();
        dto.id = l.id;
        dto.name = l.name;
        dto.column_name = l.column_name;
        dto.jsonpath = l.jsonpath;

        return dto;
    }

    public static ExtractorDao to(Extractor dto) {
        if ( dto == null ) {
            return null;
        }
        ExtractorDao l = new ExtractorDao();
        l.id = dto.id;
        l.name =  dto.name;
        l.column_name = dto.column_name;
        l.jsonpath = dto.jsonpath;

        return l;
    }

}
