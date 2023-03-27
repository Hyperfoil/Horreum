package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Banner {

    public Integer id;

    public Instant created;

    @JsonProperty( required = true )
    public boolean active;
    @JsonProperty(required = true)
    public String severity;

    @JsonProperty(required = true)
    public String title;

    public String message;

    public Banner() {
    }
}
