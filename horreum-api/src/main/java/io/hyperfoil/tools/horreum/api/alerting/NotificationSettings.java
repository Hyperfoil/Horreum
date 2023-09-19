package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class NotificationSettings {
    public Integer id;
    @NotNull
    @JsonProperty( required = true )
    public String name;
    @JsonProperty( required = true )
    public boolean isTeam;
    @NotNull
    @JsonProperty( required = true )
    public String method;
    public String data;
    @NotNull
    @JsonProperty( required = true )
    public boolean disabled;

}
