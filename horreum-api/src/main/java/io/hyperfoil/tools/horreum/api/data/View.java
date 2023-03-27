package io.hyperfoil.tools.horreum.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class View {
    @JsonProperty(required = true)
    public Integer id;
    @JsonProperty(required = true)
    public String name;
    public Integer testId;
    @JsonProperty(required = true)
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
        if(components != null)
            components.stream().forEach( c -> {c.id = null;});
    }
}
