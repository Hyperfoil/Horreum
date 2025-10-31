import {KeycloakConfig} from "../generated";
import {User, UserManager, WebStorageStateStore} from "oidc-client-ts";

export const createUserManager = (config: KeycloakConfig): UserManager => {
    return new UserManager({
        authority: config.url + "/realms/horreum",
        client_id: config.clientId ?? "",
        redirect_uri: `${window.location.origin}/callback-sso`,
        post_logout_redirect_uri: window.location.origin,
        userStore: new WebStorageStateStore({ store: window.sessionStorage }),
        monitorSession: true, // this allows cross tab login/logout detection
    })
}

export const onSigninCallback = (_user: User | undefined) => {
    window.history.replaceState({}, document.title, window.location.pathname);
};
