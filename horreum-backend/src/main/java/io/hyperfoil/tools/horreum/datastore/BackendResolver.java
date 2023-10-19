package io.hyperfoil.tools.horreum.datastore;

import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class BackendResolver {
    @Inject
    @All
    List<Datastore> backendStores;

    public Datastore getBackend(DatastoreType type){
        return backendStores.stream()
                .filter( store -> store.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown datastore type: " + type));
    }


}
