package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class ConditionConfig {
   @NotNull
   public String name;
   @NotNull
   public String title;
   @NotNull
   public String description;
   @NotNull
   public List<Component> ui = new ArrayList<>();
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
      public String name;
      @NotNull
      public String title;
      @NotNull
      public String description;
      @NotNull
      public ComponentType type;
      @NotNull
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
