import React from 'react';
import { useDispatch, useSelector } from 'react-redux'

import {
  Button,
} from '@patternfly/react-core';

import Keycloak from "keycloak-js"

import store, { State } from './store'
import { fetchApi } from './services/api';
import { alertAction, CLEAR_ALERT } from './alerts'

const INIT = "auth/INIT"
const REGISTER_AFTER_LOGIN = "auth/REGISTER_AFTER_LOGIN"
const STORE_PROFILE = "auth/STORE_PROFILE"
const AFTER_LOGOUT = "auth/AFTER_LOGOUT"

export type Access = 0 | 1 | 2

export class AuthState {
  keycloak?: Keycloak.KeycloakInstance = undefined;
  userProfile?: Keycloak.KeycloakProfile;
  initPromise?: Promise<boolean> = undefined;
  afterLogin: { name: string, func(): void }[] = [];
}

const initialState = new AuthState()

interface InitAction {
   type: typeof INIT,
   keycloak: Keycloak.KeycloakInstance,
   initPromise?: Promise<boolean>,
}

interface RegisterAfterLoginAction {
   type: typeof REGISTER_AFTER_LOGIN,
   name: string,
   func(): void,
}

interface StoreProfileAction {
   type: typeof STORE_PROFILE,
   profile: Keycloak.KeycloakProfile,
}

interface AfterLogoutAction {
   type: typeof AFTER_LOGOUT,
}

type AuthActions = InitAction | RegisterAfterLoginAction | StoreProfileAction | AfterLogoutAction

export const reducer = (state = initialState, action: AuthActions) => {
   switch (action.type) {
      case INIT:
         state.keycloak = action.keycloak;
         if (action.initPromise) {
            state.initPromise = action.initPromise;
         }
         break;
      case REGISTER_AFTER_LOGIN:
         state.afterLogin = [...state.afterLogin.filter(({ name, func }) => name !== action.name),
                             { name: action.name, func: action.func }]
         break;
      case STORE_PROFILE:
         state.userProfile = action.profile
         break
      case AFTER_LOGOUT:
         state.userProfile = undefined;
         state.initPromise = undefined;
      default:
   }
   return state;
}

export const registerAfterLogin = (name: string, func: () => void) => {
   return {
      type: REGISTER_AFTER_LOGIN,
      name: name,
      func: func,
   }
}

const keycloakSelector = (state: State) => {
   return state.auth.keycloak;
}

export const tokenSelector = (state: State) => {
   return state.auth.keycloak && state.auth.keycloak.token
}

export const roleToName = (role?: string) => {
   return role ? (role.charAt(0).toUpperCase() + role.slice(1, -5)) : undefined
}

export const userProfileSelector = (state: State) => {
   return state.auth.userProfile
}

export const isAuthenticatedSelector = (state: State) => {
   let keycloak = state.auth.keycloak
   return !!keycloak && keycloak.authenticated;
}

export const isAdminSelector = (state: State) => {
   let keycloak = state.auth.keycloak
   return !!keycloak && keycloak.hasRealmRole("admin")
}

export const rolesSelector = (state: State): string[] => {
   let keycloak = state.auth.keycloak;
   return keycloak && keycloak.realmAccess ? keycloak.realmAccess.roles : []
}

export const useTester = (owner?: string) => {
   const roles = useSelector(rolesSelector)
   return roles.includes("tester") && (!owner || roles.includes(owner))
}

export const defaultRoleSelector = (state: State) => {
   let teamRoles = rolesSelector(state).filter(r => r.endsWith("-team")).sort()
   return teamRoles.length > 0 ? teamRoles[0] : undefined;
}

export const initKeycloak = (state: State) => {
   let keycloak = keycloakSelector(state);
   let keycloakPromise;
   if (!keycloak) {
      keycloakPromise = fetchApi("/api/config/keycloak")
         .then(response => Keycloak(response))
   } else {
      keycloakPromise = Promise.resolve(keycloak)
   }
   keycloakPromise.then(keycloak => {
      let initPromise: Promise<boolean> | undefined = undefined;
      if (!keycloak.authenticated) {
        // Typecast required due to https://github.com/keycloak/keycloak/pull/5858
        initPromise = keycloak.init({
          onLoad: "check-sso",
          promiseType: 'native',
        } as Keycloak.KeycloakInitOptions);
        (initPromise as Promise<boolean>).then(authenticated => {
          store.dispatch({type: CLEAR_ALERT })
          store.getState().auth.afterLogin.forEach(a => a.func())
          if (authenticated) {
            keycloak.loadUserProfile()
               .then(profile => store.dispatch({ type: STORE_PROFILE, profile }))
               .catch(error => store.dispatch(alertAction("PROFILE_FETCH_FAILURE", "Failed to fetch user profile", error)))
          }
        })
      }
      store.dispatch({ type: INIT, keycloak: keycloak, initPromise: initPromise })
   })
}

export const TryLoginAgain = () => {
   const keycloak = useSelector(keycloakSelector)
   return keycloak ? (<>Try <Button variant="link" onClick={() => keycloak.login()}>log in again</Button></>) : null
}

export const LoginLogout = () => {
   const keycloak = useSelector(keycloakSelector)
   const dispatch = useDispatch()
   if (!keycloak) {
      return (<></>)
   } else if (keycloak.authenticated) {
      return (
         <Button onClick={ () => {
            keycloak.logout()
            dispatch({ type: AFTER_LOGOUT })
         }}>Log out</Button>
      )
   } else {
      return (
         <Button onClick={ () => keycloak.login() }>Log in</Button>
      )
   }
}

export const accessName = (access: Access) => {
   switch (access) {
       case 0: return 'PUBLIC'
       case 1: return 'PROTECTED'
       case 2: return 'PRIVATE'
       default: return String(access);
   }
}