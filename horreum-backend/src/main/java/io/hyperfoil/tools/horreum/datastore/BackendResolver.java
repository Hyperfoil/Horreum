package io.hyperfoil.tools.horreum.datastore;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.quarkus.arc.All;

@ApplicationScoped
public class BackendResolver {
    @Inject
    @All
    List<Datastore> backendStores;

    public Datastore getBackend(DatastoreType type) {
        return backendStores.stream()
                .filter(store -> store.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown datastore type: " + type));
    }

}
