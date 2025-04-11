package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.entity.data.ActionDAO;
import io.hyperfoil.tools.horreum.svc.ActionEvent;

public class ActionMapper {

    public static Action from(ActionDAO action) {
        return new Action(action.id, action.event.getValue(), action.type,
                action.config, action.secrets, action.testId, action.active, action.runAlways);

    }

    public static ActionDAO to(Action action) {
        return new ActionDAO(action.id, ActionEvent.fromValue(action.event), action.type,
                action.config, action.secrets, action.testId, action.active, action.runAlways);
    }
}
