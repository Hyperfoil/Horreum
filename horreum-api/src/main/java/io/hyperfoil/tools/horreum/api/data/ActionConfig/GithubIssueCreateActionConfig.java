package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class GithubIssueCreateActionConfig extends BaseActionConfig {

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub repo owner")
    public String owner;

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub repo name")
    public String repo;

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub issue title")
    public String title;

    @Schema(type = SchemaType.STRING, required = true, description = "Object markdown formatter")
    public String formatter;

    public GithubIssueCreateActionConfig() {
        this.type = ActionType.GITHUB_ISSUE_CREATE.toString();
    }
}
