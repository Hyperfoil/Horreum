package io.hyperfoil.tools.horreum.api.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;

@Schema(type = SchemaType.OBJECT, description = "A configuration object for Change detection models")
public class ConditionConfig {
    @NotNull
    @Schema(description = "Name of Change detection model", example = ChangeDetectionModelType.names.FIXED_THRESHOLD)
    public String name;
    @NotNull
    @Schema(description = "UI name for change detection model", example = "Fixed Threshold")
    public String title;
    @NotNull
    @Schema(description = "Change detection model description", example = "This model checks that the datapoint value is within fixed bounds.")
    public String description;
    @NotNull
    @Schema(description = "A list of UI components for dynamically building the UI components")
    public List<Component> ui = new ArrayList<>();
    @Schema(description = "A dictionary of UI default configuration items for dynamically building the UI components")
    public Map<String, JsonNode> defaults = new HashMap<>();

    public ConditionConfig(String name, String title, String description) {
        this.name = name;
        this.title = title;
        this.description = description;
    }

    public Component addComponent(String name, JsonNode defaultValue, ComponentType type, String title, String description) {
        defaults.put(name, defaultValue);
        Component component = new Component(name, title, description, type);
        ui.add(component);
        return component;
    }

    public ConditionConfig addComponent(String name, ComponentTemplate template, String title, String description) {
        Component component = addComponent(name, template.getDefault(), template.getType(), title, description);
        template.addProperties(component);
        return this;
    }

    @Schema(name = "ConditionComponent")
    public class Component {
        @NotNull
        @Schema(description = "Change detection model component name", example = "min")
        public String name;
        @NotNull
        @Schema(description = "Change detection model component title", example = "Minimum")
        public String title;
        @NotNull
        @Schema(description = "Change detection model component description", example = "Lower bound for acceptable datapoint values.")
        public String description;
        @NotNull
        @Schema(type = SchemaType.OBJECT, implementation = ComponentType.class, description = "UI Component type", example = "\"LOG_SLIDER\"")
        public ComponentType type;
        @NotNull
        @Schema(description = "Map of properties for component", example = "")
        public Map<String, Object> properties = new HashMap<>();

        private Component(String name, String title, String description, ComponentType type) {
            this.name = name;
            this.title = title;
            this.description = description;
            this.type = type;
        }

        public Component addProperty(String property, Object value) {
            if (properties.put(property, value) != null) {
                throw new IllegalArgumentException(property + " already set.");
            }
            return this;
        }

        public ConditionConfig end() {
            return ConditionConfig.this;
        }
    }

    public enum ComponentType {
        LOG_SLIDER,
        ENUM,
        NUMBER_BOUND,
        SWITCH,
    }

    public interface ComponentTemplate {
        ComponentType getType();

        JsonNode getDefault();

        void addProperties(Component component);
    }

    public static class LogSliderComponent implements ComponentTemplate {
        private final double scale;
        private final double min;
        private final double max;
        private final double defaultValue;
        private final boolean discrete;
        private final String unit;

        public LogSliderComponent(double scale, double min, double max, double defaultValue, boolean discrete, String unit) {
            this.scale = scale;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
            this.discrete = discrete;
            this.unit = unit;
        }

        @Override
        public ComponentType getType() {
            return ComponentType.LOG_SLIDER;
        }

        @Override
        public JsonNode getDefault() {
            return JsonNodeFactory.instance.numberNode(defaultValue);
        }

        @Override
        public void addProperties(Component component) {
            component
                    .addProperty("scale", scale)
                    .addProperty("min", min)
                    .addProperty("max", max)
                    .addProperty("unit", unit)
                    .addProperty("discrete", discrete);
        }
    }

    public static class EnumComponent implements ComponentTemplate {
        private final Map<String, String> options = new HashMap<>();
        private final String defaultValue;

        public EnumComponent(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public EnumComponent add(String name, String title) {
            options.put(name, title);
            return this;
        }

        @Override
        public ComponentType getType() {
            return ComponentType.ENUM;
        }

        @Override
        public JsonNode getDefault() {
            return JsonNodeFactory.instance.textNode(defaultValue);
        }

        @Override
        public void addProperties(Component component) {
            component.addProperty("options", options);
        }
    }

    public static class NumberBound implements ComponentTemplate {
        @Override
        public ComponentType getType() {
            return ComponentType.NUMBER_BOUND;
        }

        @Override
        public JsonNode getDefault() {
            return JsonNodeFactory.instance.objectNode().put("value", 0).put("inclusive", true).put("enabled", false);
        }

        @Override
        public void addProperties(Component component) {
        }
    }

    public static class SwitchComponent implements ComponentTemplate {
        @Override
        public ComponentType getType() {
            return ComponentType.SWITCH;
        }

        @Override
        public JsonNode getDefault() {
            return JsonNodeFactory.instance.booleanNode(true);
        }

        @Override
        public void addProperties(Component component) {
        }
    }
}
