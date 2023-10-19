package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;

public class DatasourceMapper {
    public static Datastore from(DatastoreConfigDAO backend) {
        Datastore dto = new Datastore();
        dto.id = backend.id;
        dto.config = backend.configuration;
        dto.name = backend.name;
        dto.type = backend.type;
        dto.access = backend.access;
        dto.owner = backend.owner;

        return dto;
    }

    public static DatastoreConfigDAO to(Datastore dto) {
        DatastoreConfigDAO backendConfigDAO = new DatastoreConfigDAO();
        backendConfigDAO.id = dto.id;
        backendConfigDAO.name = dto.name;
        backendConfigDAO.configuration = dto.config;
        backendConfigDAO.type = dto.type;
        backendConfigDAO.access = dto.access;
        backendConfigDAO.owner = dto.owner;

        return backendConfigDAO;

    }
}