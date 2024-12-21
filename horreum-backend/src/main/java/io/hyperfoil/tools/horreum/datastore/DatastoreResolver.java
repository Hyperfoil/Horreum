package io.hyperfoil.tools.horreum.datastore;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import io.quarkus.arc.All;

@ApplicationScoped
public class DatastoreResolver {
    @Inject
    @All
    List<Datastore> datastores;

    public Datastore getDatastore(DatastoreType type) {
        return datastores.stream()
                .filter(store -> store.type().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown datastore type: " + type));
    }

    public void validatedDatastoreConfig(DatastoreType type, Object config) {
        io.hyperfoil.tools.horreum.datastore.Datastore datastoreImpl;
        try {
            datastoreImpl = this.getDatastore(type);
        } catch (IllegalStateException e) {
            throw ServiceException.badRequest("Unknown datastore type: " + type
                    + ". Please try again, if the problem persists please contact the system administrator.");
        }

        if (datastoreImpl == null) {
            throw ServiceException.badRequest("Unknown datastore type: " + type);
        }

        String error = datastoreImpl.validateConfig(config);

        if (error != null) {
            throw ServiceException.badRequest(error);
        }

    }

}
