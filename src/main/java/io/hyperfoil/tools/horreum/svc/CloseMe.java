package io.hyperfoil.tools.horreum.svc;

interface CloseMe extends AutoCloseable {
   @Override
   void close();
}
