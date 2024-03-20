package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class SlackChannelMessageActionConfig extends BaseActionConfig {

    @Schema(type = SchemaType.STRING, required = true, description = "Slack channel")
    public String channel;

    @Schema(type = SchemaType.STRING, required = true, description = "Object markdown formatter")
    public String formatter;

    public SlackChannelMessageActionConfig() {
        this.type = ActionType.SLACK_MESSAGE.toString();
    }
}
