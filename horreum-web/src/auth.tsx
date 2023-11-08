import { useDispatch, useSelector } from "react-redux"

import { Button } from "@patternfly/react-core"

import { State } from "./store"
import { UserData } from "./api"
import { ThunkDispatch } from "redux-thunk"
import Keycloak, {KeycloakProfile} from "keycloak-js";

export const INIT = "auth/INIT"
export const UPDATE_DEFAULT_TEAM = "auth/UPDATE_DEFAULT_TEAM"
export const UPDATE_ROLES = "auth/UPDATE_ROLES"
export const STORE_PROFILE = "auth/STORE_PROFILE"
const AFTER_LOGOUT = "auth/AFTER_LOGOUT"

export class AuthState {
    keycloak?: Keycloak
    authenticated = false
    roles: string[] = []
    teams: string[] = []
    defaultTeam?: string = undefined
    userProfile?: KeycloakProfile
    initPromise?: Promise<boolean> = undefined
}

interface InitAction {
    type: typeof INIT
    keycloak: Keycloak
    initPromise?: Promise<boolean>
}

export interface UpdateDefaultTeamAction {
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
    profile: KeycloakProfile
}

interface AfterLogoutAction {
    type: typeof AFTER_LOGOUT
}

type AuthAction = InitAction | UpdateDefaultTeamAction | UpdateRolesAction | StoreProfileAction | AfterLogoutAction

export type AuthDispatch = ThunkDispatch<any, unknown, AuthAction >

export function reducer(state = new AuthState(), action: AuthAction) {
    // TODO: is this necessary? It seems that without that the state is not updated at times.
    state = { ...state }
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

function isTester(owner: string, roles: string[]) {
    return roles.includes(owner.slice(0, -4) + "tester")
}

export function useTester(owner?: string) {
    const roles = useSelector(rolesSelector)
    return roles.includes("tester") && (!owner || isTester(owner, roles))
}

export function managedTeamsSelector(state: State) {
    return state.auth.roles.filter(role => role.endsWith("-manager")).map(role => role.slice(0, -7) + "team")
}

export function useManagedTeams(): string[] {
    return useSelector(managedTeamsSelector)
}

export const defaultTeamSelector = (state: State) => {
    if (state.auth.defaultTeam !== undefined) {
        return state.auth.defaultTeam
    }
    const teamRoles = teamsSelector(state)
    return teamRoles.length > 0 ? teamRoles[0] : undefined
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
    const authenticated = useSelector(isAuthenticatedSelector)
    const dispatch = useDispatch()
    if (!keycloak) {
        return <Button isDisabled>Cannot log in</Button>
    }
    if (authenticated) {
        return (
            <Button
                onClick={() => {
                    keycloak?.logout({ redirectUri: window.location.origin })
                    dispatch({ type: AFTER_LOGOUT })
                }}
            >
                Log out
            </Button>
        )
    } else {
        return <Button onClick={() => keycloak?.login()}>Log in</Button>
    }
}


export function userName(user: UserData) {
    let str = ""
    if (user.firstName) {
        str += user.firstName + " "
    }
    if (user.lastName) {
        str += user.lastName + " "
    }
    if (user.firstName || user.lastName) {
        return str + " [" + user.username + "]"
    } else {
        return user.username
    }
}
