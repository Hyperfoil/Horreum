package io.hyperfoil.tools.horreum.api.alerting;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NotificationSettings {
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public String name;
    @JsonProperty(required = true)
    public boolean isTeam;
    @NotNull
    @JsonProperty(required = true)
    public String method;
    public String data;
    @NotNull
    @JsonProperty(required = true)
    public boolean disabled;

}
