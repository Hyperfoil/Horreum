package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.ActionLog;
import io.hyperfoil.tools.horreum.entity.ActionLogDAO;

public class ActionLogMapper {

    public static ActionLog from(ActionLogDAO al) {
        return new ActionLog(al.level, al.testId, al.event, al.type, al.message);
    }
}
