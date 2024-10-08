package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.alerting.TransformationLog;
import io.hyperfoil.tools.horreum.entity.alerting.TransformationLogDAO;

public class TransformationLogMapper {

    public static TransformationLog from(TransformationLogDAO tl) {
        return new TransformationLog(tl.test.id, tl.run.id, tl.level, tl.message);
    }
}
