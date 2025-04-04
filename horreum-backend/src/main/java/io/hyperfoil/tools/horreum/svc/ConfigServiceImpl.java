package io.hyperfoil.tools.horreum.svc;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.hyperfoil.tools.horreum.api.Version;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;
import io.hyperfoil.tools.horreum.api.data.datastore.DatastoreType;
import io.hyperfoil.tools.horreum.api.services.ConfigService;
import io.hyperfoil.tools.horreum.datastore.DatastoreResolver;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.mapper.DatasourceMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class ConfigServiceImpl implements ConfigService {

    //cache available dataStore configurations
    private static final List<DatastoreType.TypeConfig> datastoreTypes = Arrays.stream(DatastoreType.values())
            .map(DatastoreType::getConfig).toList();

    @ConfigProperty(name = "horreum.privacy")
    Optional<String> privacyStatement;

    @Inject
    SecurityIdentity identity;

    @Inject
    EntityManager em;

    @Inject
    DatastoreResolver backendResolver;

    @Override
    public KeycloakConfig keycloak() {
        KeycloakConfig config = new KeycloakConfig();
        config.url = getString("horreum.keycloak.url");
        config.realm = getString("horreum.keycloak.realm");
        config.clientId = getString("horreum.keycloak.clientId");
        return config;
    }

    @Override
    public VersionInfo version() {
        VersionInfo info = new VersionInfo();
        info.version = Version.getVersion();
        info.startTimestamp = startTimestamp;
        info.privacyStatement = privacyStatement.orElse(null);
        return info;
    }

    @Override
    @PermitAll
    @Transactional
    public List<Datastore> getDatastoresByTeam(String team) {
        if (team == null || team.isBlank()) {
            throw ServiceException.badRequest("Team cannot be null or blank");
        }

        String queryWhere = "where access = 0";
        Set<String> roles = identity.getRoles();
        long rolesCount = roles.stream().filter(role -> role.endsWith("-team")).count();
        if (rolesCount != 0) { //user has access to team, retrieve the team datastore as well
            queryWhere = queryWhere + " or owner in ('" + team + "')";
        }
        List<DatastoreConfigDAO> backends = DatastoreConfigDAO.list(queryWhere);
        if (!backends.isEmpty()) {
            return backends.stream().map(DatasourceMapper::from).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DatastoreType.TypeConfig> datastoreTypes() {
        return datastoreTypes;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public Integer newDatastore(Datastore datastore) {
        DatastoreConfigDAO dao = DatasourceMapper.to(datastore);
        dao.id = null;

        if (dao.owner == null) {
            List<String> uploaders = identity.getRoles().stream().filter(role -> role.endsWith("-uploader"))
                    .collect(Collectors.toList());
            if (uploaders.size() != 1) {
                Log.debugf("Failed to create datastore '%s': no owner, available uploaders: %s", dao.name, uploaders);
                throw ServiceException.badRequest(
                        "Missing owner and cannot select single default owners; this user has these uploader roles: "
                                + uploaders);
            }
            String uploader = uploaders.get(0);
            dao.owner = uploader.substring(0, uploader.length() - 9) + "-team";
        } else if (!identity.getRoles().contains(dao.owner)) {
            Log.debugf("Failed to create datastore '%s': requested owner %s, available roles: %s", dao.name, dao.owner,
                    identity.getRoles());
            throw ServiceException.badRequest("This user does not have permissions to create datastore for owner=" + dao.owner);
        }
        if (dao.access == null) {
            dao.access = Access.PRIVATE;
        }

        backendResolver.validatedDatastoreConfig(datastore.type, datastore.config);

        Log.debugf("Creating new Datastore with owner=%s and access=%s", dao.owner, dao.access);

        try {
            em.persist(dao);
            em.flush();
        } catch (Exception e) {
            Log.error("Failed to persist run", e);
            throw ServiceException.serverError("Failed to persist backend configuration");
        }
        Log.debugf("Upload flushed, backendConfig ID %d", dao.id);

        return dao.id;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public Integer updateDatastore(Datastore datastore) {
        DatastoreConfigDAO dao = DatastoreConfigDAO.findById(datastore.id);
        if (dao == null)
            throw ServiceException.notFound("Datastore with id " + datastore.id + " does not exist");

        DatastoreConfigDAO newDao = DatasourceMapper.to(datastore);

        dao.type = newDao.type;
        dao.name = newDao.name;
        dao.configuration = newDao.configuration;
        dao.access = newDao.access;

        backendResolver.validatedDatastoreConfig(datastore.type, datastore.config);

        dao.persist();

        return dao.id;

    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public DatastoreTestResponse testDatastore(Integer datastoreId) {
        return null;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public void deleteDatastore(Integer datastoreId) {
        DatastoreConfigDAO.deleteById(datastoreId);
    }

    private String getString(String propertyName) {
        return ConfigProvider.getConfig().getOptionalValue(propertyName, String.class).orElse("");
    }

}
