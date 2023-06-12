package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetectionDAO;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;

public class ChangeDetectionMapper {

    public static ChangeDetection from(ChangeDetectionDAO cd) {
        return new ChangeDetection(cd.id, cd.model, cd.config);
    }

    public static ChangeDetectionDAO to(ChangeDetection dto) {
        ChangeDetectionDAO cd = new ChangeDetectionDAO();
        cd.id = dto.id;
        cd.model = dto.model;
        cd.config = dto.config;
        return cd;
    }

}
