package io.hyperfoil.tools.horreum.exp.mapper;

import io.hyperfoil.tools.horreum.api.exp.data.LabelReducer;
import io.hyperfoil.tools.horreum.exp.data.LabelReducerDao;

public class LabelReducerMapper {

    public static LabelReducer from(LabelReducerDao l) {
        if(l == null)
            return  null;
        LabelReducer dto = new LabelReducer();
        dto.id = l.id;
        dto.function = l.function;

        return dto;
    }

    public static LabelReducerDao to(LabelReducer dto) {
        if ( dto == null ){
            return null;
        }
        LabelReducerDao l = new LabelReducerDao();
        l.id = dto.id;
        l.function = dto.function;

        return l;
    }

}
