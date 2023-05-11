package io.hyperfoil.tools.horreum.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.vertx.ext.web.Router;

/*
   Redirects unknown URLs to the default /index.html so that React can try and render them as part of browser Url History
 */
@ApplicationScoped
public class RouteFilter {
   private static final String[] PATH_PREFIXES = { "/api/", "/connect", "/dev" };
   private static final Pattern FILE_NAME_PATTERN = Pattern.compile(".*[.][a-zA-Z\\d]+");

   public void init(@Observes Router router) {
      router.get("/*").handler(rc -> {
         String path = rc.normalizedPath();
         if (!path.equals("/") && Stream.of(PATH_PREFIXES).noneMatch(path::startsWith) && !FILE_NAME_PATTERN.matcher(path).matches()) {
            rc.reroute("/");
         } else {
            rc.next();
         }
      });
   }
}
