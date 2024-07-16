package io.hyperfoil.tools.horreum.exp.mapper;

import io.hyperfoil.tools.horreum.api.exp.data.Label;
import io.hyperfoil.tools.horreum.exp.data.LabelDAO;

import java.util.stream.Collectors;

public class LabelMapper {
    public static Label from(LabelDAO l) {
        if(l == null)
            return  null;
        Label dto = new Label();
        dto.id = l.id;
        dto.name = l.name;
        dto.scalarMethod = l.scalarMethod;
        dto.multiType = l.multiType;
        dto.uri = l.uri;
        dto.parent_uri = l.parent_uri;
        dto.target_schema = l.target_schema;
//        dto.parent = TestMapper.from(l.parent);
        dto.reducer = LabelReducerMapper.from(l.reducer);
        dto.extractors = l.extractors.stream().map(ExtractorMapper::from).collect(Collectors.toList());

        return dto;
    }

    public static LabelDAO to(Label dto) {
        if ( dto == null ) {
            return null;
        }
        LabelDAO l = new LabelDAO();
        l.id = dto.id;
        l.name = dto.name;
        l.scalarMethod = dto.scalarMethod;
        l.multiType = dto.multiType;
        l.uri = dto.uri;
        l.parent_uri = dto.parent_uri;
        l.target_schema = dto.target_schema;
        l.parent = TestMapper.to(dto.parent);
        l.reducer = LabelReducerMapper.to(dto.reducer);

        l.extractors = dto.extractors.stream().map(ExtractorMapper::to).collect(Collectors.toList());

        return l;
    }
}
