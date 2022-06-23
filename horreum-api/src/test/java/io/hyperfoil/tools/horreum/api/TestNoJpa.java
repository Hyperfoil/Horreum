package io.hyperfoil.tools.horreum.api;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.horreum.entity.json.Run;

public class TestNoJpa {
   /**
    * NOTE: THIS TEST MIGHT FAIL IN YOUR IDE. It should pass in Maven build.
    * In order to let IDE detect the sources we're symlinking Java sources from the horreum-model
    * module, but skipping compilation (replacing with horreum-api-generator invocation).
    * However IDEs often compile sources on its own. In IntelliJ you can exclude it from compilation
    * in project settings, Build, Execution, Deployment/Compiler/Excludes and just use what Maven creates.
    */
   @Test
   public void testNoJpa() {
      for (Annotation annotation: Run.class.getDeclaredAnnotations()) {
         assertNotEquals("javax.persistence", annotation.annotationType().getPackageName());
         assertNotEquals("org.hibernate.annotations", annotation.annotationType().getPackageName());
      }
   }
}
