package io.hyperfoil.tools.horreum.api.data;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

public class View {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public String name;
    //@NotNull - we can not enforce this check until we have clean workflows in the UI
    // atm it is possible to have a new test in the UI and create an experiment profile
    // before the test is saved, therefore the test might not have an ID
    public Integer testId;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, description = "List of components for this view")
    public List<ViewComponent> components;

    public View() {
    }

    public View(String name, Integer testId) {
        this.name = name;
        this.testId = testId;
    }

    @Override
    public String toString() {
        return "View{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", testId=" + testId +
                ", components=" + components +
                '}';
    }

    public void clearId() {
        id = null;
        if (components != null)
            components.stream().forEach(c -> {
                c.id = null;
            });
    }
}
