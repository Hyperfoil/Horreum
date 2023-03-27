package io.hyperfoil.tools.horreum.api.alerting;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NotificationSettings {
    public Integer id;
    @JsonProperty( required = true )
    public String name;
    @JsonProperty( required = true )
    public boolean isTeam;
    @JsonProperty( required = true )
    public String method;
    public String data;
    @JsonProperty( required = true )
    public boolean disabled;

}
