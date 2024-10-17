package io.hyperfoil.tools.horreum.mapper;

import java.util.stream.Collectors;

import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.entity.data.ViewDAO;

public class TestMapper {
    public static Test from(TestDAO t) {
        Test dto = new Test();
        dto.id = t.id;
        dto.name = t.name;
        dto.folder = t.folder;
        dto.description = t.description;
        dto.owner = t.owner;
        dto.access = t.access;
        dto.datastoreId = t.backendConfig == null ? 1 : t.backendConfig.id;
        dto.timelineLabels = t.timelineLabels;
        dto.timelineFunction = t.timelineFunction;
        dto.fingerprintLabels = t.fingerprintLabels;
        dto.fingerprintFilter = t.fingerprintFilter;
        dto.compareUrl = t.compareUrl;
        dto.notificationsEnabled = t.notificationsEnabled;
        if (t.transformers != null) {
            dto.transformers = t.transformers.stream().map(TransformerMapper::from).collect(Collectors.toList());
        }
        return dto;
    }

    public static TestDAO to(Test dto) {
        if (dto == null) {
            return null;
        }
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
        if (dto.datastoreId == null) {
            dto.datastoreId = 1; //by default we will push data into postgres
        }
        t.backendConfig = DatastoreConfigDAO.findById(dto.datastoreId);
        t.views = ViewDAO.<ViewDAO> find("test.id", dto.id).list();
        if (dto.transformers != null) {
            t.transformers = dto.transformers.stream().map(TransformerMapper::to).collect(Collectors.toList());
        }
        return t;
    }
}
