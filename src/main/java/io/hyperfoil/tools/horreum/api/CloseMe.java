package io.hyperfoil.tools.horreum.api;

interface CloseMe extends AutoCloseable {
   @Override
   void close();
}
