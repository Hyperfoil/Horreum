package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.databind.JsonNode;

public class ChangeDetectionModelConfig {
   @NotNull
   public String name;
   @NotNull
   public String title;
   @NotNull
   public String description;
   @NotNull
   public List<Component> ui = new ArrayList<>();
   public Map<String, JsonNode> defaults = new HashMap<>();

   public ChangeDetectionModelConfig(String name, String title, String description) {
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

   @Schema(name = "ChangeDetectionComponent")
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

      public ChangeDetectionModelConfig end() {
         return ChangeDetectionModelConfig.this;
      }
   }

   public enum ComponentType {
      LOG_SLIDER,
      ENUM,
   }
}
