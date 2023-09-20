package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonpathValidation {
    @JsonProperty(required = true)
    public boolean valid;
    public String jsonpath;
    public int errorCode;
    public String sqlState;
    public String reason;
    public String sql;
}
