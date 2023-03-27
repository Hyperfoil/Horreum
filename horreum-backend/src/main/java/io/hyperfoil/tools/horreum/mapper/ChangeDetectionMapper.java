package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetectionDAO;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;

public class ChangeDetectionMapper {

    public static ChangeDetection from(ChangeDetectionDAO cd) {
        ChangeDetection dto = new ChangeDetection();
        dto.id = cd.id;
        dto.config = cd.config;
        dto.model = cd.model;

        return dto;
    }
}
