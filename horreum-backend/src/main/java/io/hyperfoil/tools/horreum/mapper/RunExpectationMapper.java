package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.RunExpectationDAO;
import io.hyperfoil.tools.horreum.api.alerting.RunExpectation;

public class RunExpectationMapper {

    public static RunExpectation from(RunExpectationDAO re) {
        RunExpectation dto = new RunExpectation();
        dto.id = re.id;
        dto.testId = re.testId;
        dto.expectedBy = re.expectedBy;
        dto.expectedBefore = re.expectedBefore;
        dto.backlink = re.backlink;

        return dto;
    }
}
