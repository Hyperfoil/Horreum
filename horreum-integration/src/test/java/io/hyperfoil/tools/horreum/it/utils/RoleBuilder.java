package io.hyperfoil.tools.horreum.it.utils;

import org.keycloak.representations.idm.RoleRepresentation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 * copied from org.keycloak.testsuite.util.RoleBuilder
 */
public class RoleBuilder {


    private RoleRepresentation rep = new RoleRepresentation();

    public static RoleBuilder create() {
        return new RoleBuilder();
    }

    private RoleBuilder() {
    }

    public RoleBuilder id(String id) {
        rep.setId(id);
        return this;
    }

    public RoleBuilder name(String name) {
        rep.setName(name);
        return this;
    }

    public RoleBuilder description(String description) {
        rep.setDescription(description);
        return this;
    }

    public RoleBuilder composite() {
        rep.setComposite(true);
        return this;
    }

    public RoleBuilder attributes(Map<String, List<String>> attributes) {
        rep.setAttributes(attributes);
        return this;
    }

    public RoleBuilder singleAttribute(String name, String value) {
        rep.singleAttribute(name, value);
        return this;
    }

    private void checkCompositesNull() {
        if (rep.getComposites() == null) {
            rep.setComposites(new RoleRepresentation.Composites());
        }
    }

    public RoleBuilder realmComposite(RoleRepresentation role) {
        return realmComposite(role.getName());
    }

    public RoleBuilder realmComposite(String compositeRole) {
        checkCompositesNull();

        if (rep.getComposites().getRealm() == null) {
            rep.getComposites().setRealm(new HashSet<String>());
        }

        rep.getComposites().getRealm().add(compositeRole);
        return this;
    }

    public RoleBuilder clientComposite(String client, RoleRepresentation compositeRole) {
        return clientComposite(client, compositeRole.getName());
    }

    public RoleBuilder clientComposite(String client, String compositeRole) {
        checkCompositesNull();

        if (rep.getComposites().getClient() == null) {
            rep.getComposites().setClient(new HashMap<String, List<String>>());
        }

        if (rep.getComposites().getClient().get(client) == null) {
            rep.getComposites().getClient().put(client, new LinkedList<String>());
        }

        rep.getComposites().getClient().get(client).add(compositeRole);
        return this;
    }

    public RoleRepresentation build() {
        return rep;
    }

}
