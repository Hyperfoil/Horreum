package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.MissingDataRuleDAO;
import io.hyperfoil.tools.horreum.api.alerting.MissingDataRule;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;

public class MissingDataRuleMapper {
    public static MissingDataRule from(MissingDataRuleDAO rule) {
        MissingDataRule dto = new MissingDataRule();
        dto.id = rule.id;
        dto.name = rule.name;
        dto.condition = rule.condition;
        dto.labels = rule.labels;
        dto.lastNotification = rule.lastNotification;
        dto.maxStaleness = rule.maxStaleness;
        dto.testId = rule.testId();

        return dto;
    }

    public static MissingDataRuleDAO to(MissingDataRule dto) {
        MissingDataRuleDAO rule = new MissingDataRuleDAO();
        rule.id = dto.id;
        rule.name = dto.name;
        rule.condition = dto.condition;
        rule.labels = dto.labels;
        rule.lastNotification = dto.lastNotification;
        rule.maxStaleness = dto.maxStaleness;
        TestDAO test = new TestDAO();
        test.id = dto.testId;
        rule.test = test;

        return rule;
    }
}
