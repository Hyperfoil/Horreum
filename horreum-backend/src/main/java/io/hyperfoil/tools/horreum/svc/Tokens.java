package io.hyperfoil.tools.horreum.svc;

import java.security.SecureRandom;

final class Tokens {
   private static final SecureRandom random = new SecureRandom();
   private Tokens() {}

   static String generateToken() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 5; ++i) {
         sb.append(String.format("%016x", random.nextLong()));
      }
      return sb.toString();
   }
}
