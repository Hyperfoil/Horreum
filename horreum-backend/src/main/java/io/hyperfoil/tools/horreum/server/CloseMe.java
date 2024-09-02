package io.hyperfoil.tools.horreum.server;

public interface CloseMe extends AutoCloseable {
    @Override
    void close();
}
