import Keycloak, {KeycloakConfig, KeycloakInitOptions} from "keycloak-js"
import fetchival from "fetchival"

import store, { State } from "./store"
import { userApi } from "./api"
import { CLEAR_ALERT } from "./alerts"
import { noop } from "./utils"
import { keycloakSelector, INIT, STORE_PROFILE, UPDATE_DEFAULT_TEAM, UPDATE_ROLES } from "./auth"

export function initKeycloak(state: State) {
    const keycloak = keycloakSelector(state)
    let keycloakPromise
    if (!keycloak) {
        keycloakPromise = fetchival("/api/config/keycloak", { responseAs: "json" })
            .get()
            .then((response: any) => new Keycloak(response as KeycloakConfig))
    } else {
        keycloakPromise = Promise.resolve(keycloak)
    }
    keycloakPromise
        .then((keycloak: Keycloak) => {
            let initPromise: Promise<boolean> | undefined = undefined
            if (!keycloak.authenticated) {
                // Typecast required due to https://github.com/keycloak/keycloak/pull/5858
                initPromise = keycloak.init({
                    onLoad: "check-sso",
                    silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
                    promiseType: "native",
                } as KeycloakInitOptions)
                initPromise?.then(authenticated => {
                    store.dispatch({ type: CLEAR_ALERT })
                    store.dispatch({
                        type: UPDATE_ROLES,
                        authenticated,
                        roles: keycloak?.realmAccess?.roles || [],
                    })
                    if (authenticated) {
                        keycloak
                            .loadUserProfile()
                            .then(profile => store.dispatch({ type: STORE_PROFILE, profile }))
                            .catch(error =>
                                console.log(error)
                                //TODO: hook into alerting state
                                // store.dispatch(
                                //     alertAction("PROFILE_FETCH_FAILURE", "Failed to fetch user profile", error)
                                // )
                            )
                        userApi.defaultTeam().then(
                            response => store.dispatch({ type: UPDATE_DEFAULT_TEAM, team: response || undefined }),
                            error =>
                                console.log(error)
                                //TODO: hook into alerting state
                                // store.dispatch(
                                //     alertAction("DEFAULT_ROLE_FETCH_FAILURE", "Cannot retrieve default role", error)
                                // )
                        )
                        keycloak.onTokenExpired = () =>
                            keycloak.updateToken(30).catch(e => console.log("Expired token update failed: " + e))
                    } else {
                        store.dispatch({ type: STORE_PROFILE, profile: {} })
                    }
                })
            }
            store.dispatch({ type: INIT, keycloak: keycloak, initPromise: initPromise })
        })
        .catch(noop)
}
