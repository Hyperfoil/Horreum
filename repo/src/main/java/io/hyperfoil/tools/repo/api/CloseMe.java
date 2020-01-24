package io.hyperfoil.tools.repo.api;

interface CloseMe extends AutoCloseable {
   @Override
   void close();
}
