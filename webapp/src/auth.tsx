import { useDispatch, useSelector } from "react-redux"

import { Button } from "@patternfly/react-core"

import Keycloak from "keycloak-js"

import store, { State } from "./store"
import { fetchApi } from "./services/api"
import { alertAction, CLEAR_ALERT } from "./alerts"
import { noop } from "./utils"

const INIT = "auth/INIT"
const UPDATE_DEFAULT_TEAM = "auth/UPDATE_DEFAULT_TEAM"
const UPDATE_ROLES = "auth/UPDATE_ROLES"
const STORE_PROFILE = "auth/STORE_PROFILE"
const AFTER_LOGOUT = "auth/AFTER_LOGOUT"

export type Access = 0 | 1 | 2

export class AuthState {
    keycloak?: Keycloak.KeycloakInstance = undefined
    authenticated = false
    roles: string[] = []
    teams: string[] = []
    defaultTeam?: string = undefined
    userProfile?: Keycloak.KeycloakProfile
    initPromise?: Promise<boolean> = undefined
}

const initialState = new AuthState()

interface InitAction {
    type: typeof INIT
    keycloak: Keycloak.KeycloakInstance
    initPromise?: Promise<boolean>
}

interface UpdateDefaultTeamAction {
    type: typeof UPDATE_DEFAULT_TEAM
    team: string
}

interface UpdateRolesAction {
    type: typeof UPDATE_ROLES
    authenticated: boolean
    roles: string[]
}

interface StoreProfileAction {
    type: typeof STORE_PROFILE
    profile: Keycloak.KeycloakProfile
}

interface AfterLogoutAction {
    type: typeof AFTER_LOGOUT
}

type AuthActions = InitAction | UpdateDefaultTeamAction | UpdateRolesAction | StoreProfileAction | AfterLogoutAction

export const reducer = (state = initialState, action: AuthActions) => {
    switch (action.type) {
        case INIT:
            state.keycloak = action.keycloak
            if (action.initPromise) {
                state.initPromise = action.initPromise
            }
            break
        case UPDATE_DEFAULT_TEAM:
            state.defaultTeam = action.team
            break
        case UPDATE_ROLES:
            state.authenticated = action.authenticated
            state.roles = [...action.roles]
            state.teams = action.roles.filter(role => role.endsWith("-team")).sort()
            break
        case STORE_PROFILE:
            state.userProfile = action.profile
            break
        case AFTER_LOGOUT:
            state.userProfile = undefined
            state.initPromise = undefined
            state.authenticated = false
            state.roles = []
            state.teams = []
            state.defaultTeam = undefined
            break
        default:
    }
    return state
}

export const keycloakSelector = (state: State) => {
    return state.auth.keycloak
}

export const tokenSelector = (state: State) => {
    return state.auth.keycloak && state.auth.keycloak.token
}

export const teamToName = (team?: string) => {
    return team ? team.charAt(0).toUpperCase() + team.slice(1, -5) : undefined
}

export const userProfileSelector = (state: State) => {
    return state.auth.userProfile
}

export const isAuthenticatedSelector = (state: State) => {
    return state.auth.authenticated
}

export const isAdminSelector = (state: State) => {
    return state.auth.roles.includes("admin")
}

export const teamsSelector = (state: State): string[] => {
    return state.auth.teams
}

function rolesSelector(state: State) {
    return state.auth.roles
}

export const useTester = (owner?: string) => {
    const roles = useSelector(rolesSelector)
    return roles.includes("tester") && (!owner || roles.includes(owner.slice(0, -4) + "tester"))
}

export function useManagedTeams(): string[] {
    const roles = useSelector(rolesSelector)
    return roles.filter(role => role.endsWith("-manager")).map(role => role.slice(0, -4) + "team")
}

export const defaultTeamSelector = (state: State) => {
    if (state.auth.defaultTeam !== undefined) {
        return state.auth.defaultTeam
    }
    const teamRoles = teamsSelector(state)
    return teamRoles.length > 0 ? teamRoles[0] : undefined
}

export const initKeycloak = (state: State) => {
    const keycloak = keycloakSelector(state)
    let keycloakPromise
    if (!keycloak) {
        keycloakPromise = fetchApi("/api/config/keycloak").then(response => Keycloak(response))
    } else {
        keycloakPromise = Promise.resolve(keycloak)
    }
    keycloakPromise
        .then(keycloak => {
            let initPromise: Promise<boolean> | undefined = undefined
            if (!keycloak.authenticated) {
                // Typecast required due to https://github.com/keycloak/keycloak/pull/5858
                initPromise = keycloak.init({
                    onLoad: "check-sso",
                    silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
                    promiseType: "native",
                } as Keycloak.KeycloakInitOptions)
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
                                store.dispatch(
                                    alertAction("PROFILE_FETCH_FAILURE", "Failed to fetch user profile", error)
                                )
                            )
                        fetchApi("/api/user/defaultTeam", null, "GET", { accept: "text/plain" }, "text").then(
                            response => store.dispatch({ type: UPDATE_DEFAULT_TEAM, team: response || undefined }),
                            error =>
                                store.dispatch(
                                    alertAction("DEFAULT_ROLE_FETCH_FAILURE", "Cannot retrieve default role", error)
                                )
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

export function updateDefaultRole(team: string) {
    return fetchApi("/api/user/defaultTeam", team, "POST", { "content-type": "text/plain" }).then(_ =>
        store.dispatch({ type: UPDATE_DEFAULT_TEAM, team })
    )
}

export const TryLoginAgain = () => {
    const keycloak = useSelector(keycloakSelector)
    return keycloak ? (
        <>
            Try{" "}
            <Button variant="link" onClick={() => keycloak.login()}>
                log in again
            </Button>
        </>
    ) : null
}

export const LoginLogout = () => {
    const keycloak = useSelector(keycloakSelector)
    // for some reason isAuthenticatedSelector would not return correct value at times (Redux bug?)
    const authenticated = useSelector(s => (s as State).auth.authenticated)
    const dispatch = useDispatch()
    if (!keycloak) {
        return <Button isDisabled>Cannot log in</Button>
    }
    if (authenticated) {
        return (
            <Button
                onClick={() => {
                    keycloak.logout()
                    dispatch({ type: AFTER_LOGOUT })
                }}
            >
                Log out
            </Button>
        )
    } else {
        return <Button onClick={() => keycloak.login()}>Log in</Button>
    }
}

export const accessName = (access: Access) => {
    switch (access) {
        case 0:
            return "PUBLIC"
        case 1:
            return "PROTECTED"
        case 2:
            return "PRIVATE"
        default:
            return String(access)
    }
}
