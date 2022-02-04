package io.hyperfoil.tools.horreum.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public class ChangeDetectionModelConfig {
   public String name;
   public String title;
   public String description;
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

   public class Component {
      public String name;
      public String title;
      public String description;
      public ComponentType type;
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
