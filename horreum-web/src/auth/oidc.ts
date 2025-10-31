import {KeycloakConfig} from "../generated";
import {User, UserManager, WebStorageStateStore} from "oidc-client-ts";

export const createUserManager = (config: KeycloakConfig): UserManager => {
    return new UserManager({
        authority: config.url + "/realms/" + config.realm,
        client_id: config.clientId ?? "",
        redirect_uri: `${window.location.origin}/callback-sso`,
        post_logout_redirect_uri: window.location.origin,
        userStore: new WebStorageStateStore({ store: window.localStorage }),
        monitorSession: true, // this allows cross tab login/logout detection
        automaticSilentRenew: true,
    })
}

export const onSigninCallback = (_user: User | undefined) => {
    window.history.replaceState({}, document.title, window.location.pathname);
};
