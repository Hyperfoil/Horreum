package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.function.Function;

public class TestToken {
    public static final int READ = 1;
    public static final int MODIFY = 2;
    public static final int UPLOAD = 4;
    @JsonProperty(required = true)
    public Integer id;
    public Integer testId;
    @JsonProperty(required = true)
    private String value;
    @JsonProperty(required = true)
    public int permissions;
    @JsonProperty(required = true)
    public String description;

    public TestToken() {
    }

    @JsonSetter("value")
    public void setValue(String value) {
        this.value = value;
    }

    @JsonGetter("value")
    public String getValue() {
        return value;
    }

    public boolean hasRead() {
        return (this.permissions & 1) != 0;
    }

    public boolean hasModify() {
        return (this.permissions & 2) != 0;
    }

    public boolean hasUpload() {
        return (this.permissions & 4) != 0;
    }

    public boolean valueEquals(String value) {
        return this.value.equals(value);
    }

    public String getEncryptedValue(Function<String, String> encrypt) {
        return (String)encrypt.apply(this.value);
    }

    public void decryptValue(Function<String, String> decrypt) {
        this.value = (String)decrypt.apply(this.value);
    }

    @Override
    public String toString() {
        return "TestToken{" +
                "id=" + id +
                ", testId=" + testId +
                ", value='" + value + '\'' +
                ", permissions=" + permissions +
                ", description='" + description + '\'' +
                '}';
    }

    public void clearId() {
        id = null;
        testId = null;
    }
}
