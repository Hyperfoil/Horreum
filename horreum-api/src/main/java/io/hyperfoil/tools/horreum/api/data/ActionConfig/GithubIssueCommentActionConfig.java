package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class GithubIssueCommentActionConfig extends BaseActionConfig {

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub issue URL")
    public String issueUrl;

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub repo owner")
    public String owner;

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub repo name")
    public String repo;

    @Schema(type = SchemaType.STRING, required = true, description = "GitHub issue number")
    public String issue;

    @Schema(type = SchemaType.STRING, required = true, description = "Object markdown formatter")
    public String formatter;

    public GithubIssueCommentActionConfig() {
        this.type = ActionType.GITHUB_ISSUE_COMMENT.toString();
    }
}
