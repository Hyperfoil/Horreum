package io.hyperfoil.tools.horreum.api.data;

import java.util.function.Function;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

public class TestToken {
    public static final int READ = 1;
    public static final int MODIFY = 2;
    public static final int UPLOAD = 4;
    @JsonProperty(required = true)
    @Schema(description = "Unique Token id", example = "101")
    public Integer id;
    @Schema(description = "Test ID to apply Token", example = "201")
    public Integer testId;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Test value", example = "094678029a2aaf9a2847502273099bb3a1b2338c2b9c618ed09aef0181666e38")
    private String value;
    @NotNull
    @JsonProperty(required = true)
    //TODO: this should map to an Access object
    //    @Schema( type = SchemaType.INTEGER, implementation = Access.class,
    //            description = "Access rights for the test. This defines the visibility of the Test in the UI",
    //            example = "0")
    public int permissions;
    @NotNull
    @JsonProperty(required = true)
    @Schema(description = "Token description", example = "my reporting service token")
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
        return (String) encrypt.apply(this.value);
    }

    public void decryptValue(Function<String, String> decrypt) {
        this.value = (String) decrypt.apply(this.value);
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
