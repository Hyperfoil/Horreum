package io.hyperfoil.tools.horreum.exp.mapper;


import io.hyperfoil.tools.horreum.api.exp.data.Test;
import io.hyperfoil.tools.horreum.exp.data.TestDao;

public class TestMapper {
    public static Test from(TestDao t) {
        Test dto = new Test();
        dto.id  = t.id;
        dto.name = t.name;
        dto.labels = t.labels.stream().map(LabelMapper::from).collect(java.util.stream.Collectors.toList());

        return dto;
    }

    public static TestDao to(Test dto) {
        if(dto == null){
            return null;
        }
        TestDao t = new TestDao();
        t.id = dto.id;
        t.name = dto.name;
        t.labels = dto.labels.stream().map(LabelMapper::to).collect(java.util.stream.Collectors.toList());

        return t;
    }

}
