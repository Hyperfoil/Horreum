package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.alerting.NotificationSettings;
import io.hyperfoil.tools.horreum.entity.alerting.NotificationSettingsDAO;

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

    public static NotificationSettingsDAO to(NotificationSettings ns) {
        NotificationSettingsDAO dao = new NotificationSettingsDAO();
        dao.id = ns.id;
        dao.name = ns.name;
        dao.isTeam = ns.isTeam;
        dao.method = ns.method;
        dao.data = ns.data;
        dao.disabled = ns.disabled;

        return dao;
    }
}
