package io.hyperfoil.tools.horreum.api.data.ActionConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Arrays;

@Schema(type = SchemaType.STRING, required = true, description = "Type of Action")
public enum ActionType {
    HTTP("http", new TypeReference<HttpActionConfig>() {
    }),
    GITHUB_ISSUE_COMMENT("github-issue-comment", new TypeReference<GithubIssueCommentActionConfig>() {
    }),
    GITHUB_ISSUE_CREATE("github-issue-create", new TypeReference<GithubIssueCreateActionConfig>() {
    }),
    SLACK_MESSAGE("slack-channel-message", new TypeReference<SlackChannelMessageActionConfig>() {
    });

    private static final ActionType[] VALUES = values();

    private final String name;
    private final TypeReference<? extends BaseActionConfig> typeReference;

    private <T extends BaseActionConfig> ActionType(String name, TypeReference<T> typeReference) {
        this.typeReference = typeReference;
        this.name = name;
    }

    public <T extends BaseActionConfig> TypeReference<T> getTypeReference() {
        return (TypeReference<T>) typeReference;
    }

    @JsonCreator
    public static ActionType fromString(String str) {
        return Arrays.stream(VALUES).filter(v -> v.name.equals(str)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown action: " + str));
    }
}
