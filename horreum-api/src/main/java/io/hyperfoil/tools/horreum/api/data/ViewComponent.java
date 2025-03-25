package io.hyperfoil.tools.horreum.api.data;

import java.util.Objects;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

@Schema(type = SchemaType.OBJECT, description = "Single view component")
public class ViewComponent {
    @JsonProperty(required = true)
    public Integer id;
    @NotNull
    @JsonProperty(required = true)
    public int headerOrder;
    @NotNull
    @JsonProperty(required = true)
    public String headerName;
    @NotNull
    @JsonProperty(required = true)
    @Schema(type = SchemaType.ARRAY, implementation = String.class)
    public JsonNode labels;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String render;

    public ViewComponent() {
    }

    public ViewComponent(String headerName, String render, String... labels) {
        this.headerName = headerName;
        ArrayNode labelsNode = JsonNodeFactory.instance.arrayNode();
        for (String l : labels) {
            labelsNode.add(l);
        }

        this.labels = labelsNode;
        this.render = render;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            ViewComponent that = (ViewComponent) o;
            return this.headerOrder == that.headerOrder &&
                    Objects.equals(this.id, that.id) &&
                    Objects.equals(this.headerName, that.headerName) &&
                    Objects.equals(this.labels, that.labels) &&
                    Objects.equals(this.render, that.render);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(id, headerOrder, headerName, labels, render);
    }
}
