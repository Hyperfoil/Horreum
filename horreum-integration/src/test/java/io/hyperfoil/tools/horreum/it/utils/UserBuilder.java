package io.hyperfoil.tools.horreum.it.utils;

import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 * copied from org.keycloak.testsuite.util.UserManager
 */
public class UserBuilder {

    private final UserRepresentation rep;

    public static UserBuilder create() {
        UserRepresentation rep = new UserRepresentation();
        rep.setEnabled(Boolean.TRUE);
        return new UserBuilder(rep);
    }

    public static UserBuilder edit(UserRepresentation rep) {
        return new UserBuilder(rep);
    }

    private UserBuilder(UserRepresentation rep) {
        this.rep = rep;
    }

    public UserBuilder id(String id) {
        rep.setId(id);
        return this;
    }

    public UserBuilder username(String username) {
        rep.setUsername(username);
        return this;
    }

    public UserBuilder firstName(String firstName) {
        rep.setFirstName(firstName);
        return this;
    }

    public UserBuilder lastName(String lastName) {
        rep.setLastName(lastName);
        return this;
    }

    /**
     * This method adds additional passwords to the user.
     */
    public UserBuilder addPassword(String password) {
        if (rep.getCredentials() == null) {
            rep.setCredentials(new LinkedList<>());
        }

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        rep.getCredentials().add(credential);
        return this;
    }

    public UserBuilder addAttribute(String name, String... values) {
        if (rep.getAttributes() == null) {
            rep.setAttributes(new HashMap<>());
        }

        rep.getAttributes().put(name, Arrays.asList(values));
        return this;
    }

    /**
     * This method makes sure that there is one single password for the user.
     */
    public UserBuilder password(String password) {
        rep.setCredentials(null);
        return addPassword(password);
    }

    public UserBuilder email(String email) {
        rep.setEmail(email);
        return this;
    }

    public UserBuilder emailVerified(boolean emailVerified) {
        rep.setEmailVerified(emailVerified);
        return this;
    }

    public UserBuilder enabled(boolean enabled) {
        rep.setEnabled(enabled);
        return this;
    }

    public UserBuilder addRoles(String... roles) {
        if (rep.getRealmRoles() == null) {
            rep.setRealmRoles(new ArrayList<>());
        }
        rep.getRealmRoles().addAll(Arrays.asList(roles));
        return this;
    }

    public UserBuilder role(String client, String role) {
        if (rep.getClientRoles() == null) {
            rep.setClientRoles(new HashMap<>());
        }
        if (rep.getClientRoles().get(client) == null) {
            rep.getClientRoles().put(client, new LinkedList<>());
        }
        rep.getClientRoles().get(client).add(role);
        return this;
    }

    public UserBuilder requiredAction(String requiredAction) {
        if (rep.getRequiredActions() == null) {
            rep.setRequiredActions(new LinkedList<>());
        }
        rep.getRequiredActions().add(requiredAction);
        return this;
    }

    public UserBuilder serviceAccountId(String serviceAccountId) {
        rep.setServiceAccountClientId(serviceAccountId);
        return this;
    }

    public UserBuilder secret(CredentialRepresentation credential) {
        if (rep.getCredentials() == null) {
            rep.setCredentials(new LinkedList<>());
        }

        rep.getCredentials().add(credential);
        rep.setTotp(true);
        return this;
    }

/*
    public UserBuilder totpSecret(String totpSecret) {
        CredentialRepresentation credential = ModelToRepresentation.toRepresentation(
                OTPCredentialModel.createTOTP(totpSecret, 6, 30, HmacOTP.HMAC_SHA1));
        return secret(credential);
    }

    public UserBuilder hotpSecret(String hotpSecret) {
        CredentialRepresentation credential = ModelToRepresentation.toRepresentation(
                OTPCredentialModel.createHOTP(hotpSecret, 6, 0, HmacOTP.HMAC_SHA1));
        return secret(credential);
    }
*/

    public UserBuilder otpEnabled() {
        rep.setTotp(Boolean.TRUE);
        return this;
    }

    public UserBuilder addGroups(String... group) {
        if (rep.getGroups() == null) {
            rep.setGroups(new ArrayList<>());
        }
        rep.getGroups().addAll(Arrays.asList(group));
        return this;
    }

    public UserBuilder federatedLink(String identityProvider, String federatedUserId) {
        if (rep.getFederatedIdentities() == null) {
            rep.setFederatedIdentities(new LinkedList<>());
        }
        FederatedIdentityRepresentation federatedIdentity = new FederatedIdentityRepresentation();
        federatedIdentity.setUserId(federatedUserId);
        federatedIdentity.setUserName(rep.getUsername());
        federatedIdentity.setIdentityProvider(identityProvider);

        rep.getFederatedIdentities().add(federatedIdentity);
        return this;
    }

    public UserRepresentation build() {
        return rep;
    }

}