package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.entity.data.TestTokenDAO;
import io.hyperfoil.tools.horreum.api.data.TestToken;

import java.util.stream.Collectors;

public class TestMapper {
    public static Test from(TestDAO t) {
        Test dto = new Test();
        dto.id = t.id;
        dto.name = t.name;
        dto.folder = t.folder;
        dto.description = t.description;
        dto.owner = t.owner;
        dto.access = t.access;
        dto.timelineLabels = t.timelineLabels;
        dto.timelineFunction = t.timelineFunction;
        dto.fingerprintLabels = t.fingerprintLabels;
        dto.fingerprintFilter = t.fingerprintFilter;
        dto.compareUrl = t.compareUrl;
        dto.notificationsEnabled = t.notificationsEnabled;
        if(t.tokens != null)
            dto.tokens = t.tokens.stream().map(TestMapper::fromTestToken).collect(Collectors.toList());
        if (t.views != null)
            dto.views = t.views.stream().map(ViewMapper::from).collect(Collectors.toList());
        if (t.transformers != null)
            dto.transformers = t.transformers.stream().map(TransformerMapper::from).collect(Collectors.toList());

        return dto;
    }

    public static TestToken fromTestToken(TestTokenDAO tt) {
        TestToken dto = new TestToken();
        dto.id = tt.id;
        dto.description = tt.description;
        dto.setValue(tt.getValue());

        dto.testId = tt.test.id;
        dto.permissions = tt.permissions;

        return dto;
    }

    public static TestDAO to(Test dto) {
        TestDAO t = new TestDAO();
        t.id = dto.id;
        t.name = dto.name;
        t.folder = dto.folder;
        t.description = dto.description;
        t.owner = dto.owner;
        t.access = dto.access;
        t.timelineLabels = dto.timelineLabels;
        t.timelineFunction = dto.timelineFunction;
        t.fingerprintLabels = dto.fingerprintLabels;
        t.fingerprintFilter = dto.fingerprintFilter;
        t.compareUrl = dto.compareUrl;
        t.notificationsEnabled = dto.notificationsEnabled;
        if(dto.tokens != null)
            t.tokens = dto.tokens.stream().map(token -> TestMapper.toTestToken(token,t) ).collect(Collectors.toList());
        if (dto.views != null)
            t.views = dto.views.stream().map(ViewMapper::to).collect(Collectors.toList());
        if (dto.transformers != null)
            t.transformers = dto.transformers.stream().map(TransformerMapper::to).collect(Collectors.toList());

        return t;
    }

    private static TestTokenDAO toTestToken(TestToken dto, TestDAO t) {
        TestTokenDAO tt = new TestTokenDAO();
        tt.id = dto.id;
        tt.description = dto.description;
        tt.setValue(dto.getValue());
        tt.test = t;
        tt.permissions = dto.permissions;

        return tt;
    }
}
