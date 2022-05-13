package io.hyperfoil.tools.horreum.grafana;

import java.util.ArrayList;
import java.util.List;

public class Dashboard {
   public Integer id;
   public String uid;
   public String title;
   public TimeRange time = new TimeRange();
   public Annotations annotations = new Annotations();
   public List<Panel> panels = new ArrayList<>();
   public List<String> tags = new ArrayList<>();

   public static class Annotations {
      public List<Annotation> list = new ArrayList<>();
   }

   public static class Annotation {
      public String name;
      public String datasource = "Horreum";
      public String query;
      public boolean enable = true;
      public String iconColor = "rgba(255, 96, 96, 1)";

      public Annotation() {}

      public Annotation(String name, String query) {
         this.name = name;
         this.query = query;
      }
   }

   public static class Panel {
      public String title;
      public String type = "graph";
      public String renderer = "flot";
      public String datasource = "Horreum";
      public GridPos gridPos;
      public List<Target> targets = new ArrayList<>();

      public Panel() {}

      public Panel(String title, GridPos gridPos) {
         this.title = title;
         this.gridPos = gridPos;
      }
   }

   public static class GridPos {
      public int x, y, w, h;

      public GridPos() {}

      public GridPos(int x, int y, int w, int h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }
   }
}
