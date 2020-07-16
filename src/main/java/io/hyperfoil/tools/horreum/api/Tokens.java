package io.hyperfoil.tools.horreum.api;

import java.util.concurrent.ThreadLocalRandom;

final class Tokens {
   private Tokens() {}

   static String generateToken() {
      StringBuilder sb = new StringBuilder();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      for (int i = 0; i < 5; ++i) {
         sb.append(random.nextLong());
      }
      return sb.toString();
   }
}
