package io.hyperfoil.tools.horreum.exp.mapper;

import io.hyperfoil.tools.horreum.api.exp.data.Extractor;
import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;

public class ExtractorMapper {
    public static Extractor from(ExtractorDao l) {
        if (l == null)
            return null;
        Extractor dto = new Extractor();
        dto.id = l.id;
        dto.name = l.name;
        dto.column_name = l.column_name;
        if (l.type.equals(ExtractorDao.Type.VALUE)) {
            dto.jsonpath = l.targetLabel.name + ExtractorDao.NAME_SEPARATOR + l.jsonpath;
        } else if (l.type.equals(ExtractorDao.Type.PATH)) {
            dto.jsonpath = l.jsonpath;
        } else if (l.type.equals(ExtractorDao.Type.METADATA)) {
            dto.jsonpath = ExtractorDao.METADATA_PREFIX + l.column_name + ExtractorDao.METADATA_SUFFIX
                    + ExtractorDao.NAME_SEPARATOR + l.jsonpath;
        }
        return dto;
    }

    public static ExtractorDao to(Extractor dto) {
        if (dto == null) {
            return null;
        }
        ExtractorDao l = new ExtractorDao();
        l.id = dto.id;
        l.name = dto.name;
        l.column_name = dto.column_name;
        l.jsonpath = dto.jsonpath;

        return l;
    }

}
