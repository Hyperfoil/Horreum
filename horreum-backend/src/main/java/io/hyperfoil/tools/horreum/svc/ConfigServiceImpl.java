package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.Version;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.datastore.Datastore;
import io.hyperfoil.tools.horreum.api.services.ConfigService;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.mapper.DatasourceMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConfigServiceImpl implements ConfigService {

    private static final Logger log = Logger.getLogger(ConfigServiceImpl.class);

    @Inject
    SecurityIdentity identity;

    @Inject
    EntityManager em;


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
        info.version = Version.VERSION;
        info.startTimestamp = startTimestamp;
        return info;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @Transactional
    public List<Datastore> datastores(String team) {
        Set<String> roles = identity.getRoles();
        long rolesCount = roles.stream().filter(role -> role.endsWith("-team")).count();
        if (rolesCount == 0) {
            throw ServiceException.forbidden(String.format("User does not have permission to view backends for team: %s", team));
        }
        String queryWhere = "where access = 0 or owner in ('" + team + "')";
        List<DatastoreConfigDAO> backends = DatastoreConfigDAO.list(queryWhere);
        List<Datastore> backendList = backends.stream().map(DatasourceMapper::from).collect(Collectors.toList());
        return backendList;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public Integer newDatastore(Datastore datastore) {
        DatastoreConfigDAO dao = DatasourceMapper.to(datastore);
        dao.id = null;

        if (dao.owner == null) {
            List<String> uploaders = identity.getRoles().stream().filter(role -> role.endsWith("-uploader")).collect(Collectors.toList());
            if (uploaders.size() != 1) {
                log.debugf("Failed to create new backend %s: no owner, available uploaders: %s", dao.name, uploaders);
                throw ServiceException.badRequest("Missing owner and cannot select single default owners; this user has these uploader roles: " + uploaders);
            }
            String uploader = uploaders.get(0);
            dao.owner = uploader.substring(0, uploader.length() - 9) + "-team";
        } else if (!identity.getRoles().contains(dao.owner)) {
            log.debugf("Failed to create backend configuration %s: requested owner %s, available roles: %s", dao.name, dao.owner, identity.getRoles());
            throw ServiceException.badRequest("This user does not have permissions to upload backend configuration for owner=" + dao.owner);
        }
        if (dao.access == null) {
            dao.access = Access.PRIVATE;
        }
        log.debugf("Uploading with owner=%s and access=%s", dao.owner, dao.access);

        try {
            em.persist(dao);
            em.flush();
        } catch (Exception e) {
            log.error("Failed to persist run.", e);
            throw ServiceException.serverError("Failed to persist backend configuration");
        }
        log.debugf("Upload flushed, backendConfig ID %d", dao.id);

        return dao.id;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public Integer updateDatastore(Datastore backend){
        DatastoreConfigDAO dao = DatastoreConfigDAO.findById(backend.id);
        if ( dao == null )
            throw ServiceException.notFound("Datastore with id " + backend.id + " does not exist");

        DatastoreConfigDAO newDao = DatasourceMapper.to(backend);

        dao.type = newDao.type;
        dao.name = newDao.name;
        dao.configuration = newDao.configuration;
        dao.access = newDao.access;

        dao.persist();

        return dao.id;

    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public DatastoreTestResponse testDatastore(String datastoreId) {
        return null;
    }

    @Override
    @RolesAllowed(Roles.TESTER)
    @WithRoles
    @Transactional
    public void deleteDatastore(String datastoreId) {
        DatastoreConfigDAO.deleteById(Integer.parseInt(datastoreId));
    }

    private String getString(String propertyName) {
        return ConfigProvider.getConfig().getValue(propertyName, String.class);
    }

}
