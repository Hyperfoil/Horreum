package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettingsDAO;
import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;

public class NotificationSettingsMapper {
    public static NotificationSettings from(NotificationSettingsDAO ns) {
        NotificationSettings dto = new NotificationSettings();
        dto.id = ns.id;
        dto.name = ns.name;
        dto.isTeam = ns.isTeam;
        dto.method = ns.method;
        dto.data = ns.data;
        dto.disabled = ns.disabled;

        return dto;
    }
}
