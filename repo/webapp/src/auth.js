import React from 'react';
import { useSelector } from 'react-redux'

import {
  Alert,
  AlertActionCloseButton,
  Button,
} from '@patternfly/react-core';

import Keycloak from "keycloak-js"

import store from './store'
import { fetchApi } from './services/api';
import { CLEAR_ALERT } from './alerts'

const INIT = "auth/INIT"
const REGISTER_AFTER_LOGIN = "auth/REGISTER_AFTER_LOGIN"

const initialState = {
  keycloak: null,
  initPromise: null,
  afterLogin: []
}

export const reducer = (state = initialState, action) => {
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
      default:
   }
   return state;
}

export const registerAfterLogin = (name, func) => {
   return {
      type: REGISTER_AFTER_LOGIN,
      name: name,
      func: func,
   }
}

const keycloakSelector = state => {
   return state.auth.keycloak;
}

export const roleToName = (role) => {
   return role ? (role.charAt(0).toUpperCase() + role.slice(1, -5)) : null
}

export const isAuthenticatedSelector = state => {
   let keycloak = state.auth.keycloak
   return !!keycloak && keycloak.authenticated;
}

export const isUploaderSelector = state => {
   let keycloak = state.auth.keycloak
   return !!keycloak && keycloak.hasRealmRole("uploader")
}

export const isTesterSelector = state => {
   let keycloak = state.auth.keycloak
   return !!keycloak && keycloak.hasRealmRole("tester")
}

export const isAdminSelector = state => {
   let keycloak = state.auth.keycloak
   return !!keycloak && keycloak.hasRealmRole("admin")
}

export const rolesSelector = state => {
   let keycloak = state.auth.keycloak;
   return keycloak && keycloak.realmAccess ? keycloak.realmAccess.roles : []
}

export const defaultRoleSelector = state => {
   let teamRoles = rolesSelector(state).filter(r => r.endsWith("-team")).sort()
   return teamRoles.length > 0 ? teamRoles[0] : null;
}

export const initKeycloak = state => {
   let keycloak = keycloakSelector(state);
   let keycloakPromise;
   if (keycloak === null) {
      keycloakPromise = fetchApi("/api/config/keycloak", null)
         .then(response => Keycloak(response))
   } else {
      keycloakPromise = Promise.resolve(keycloak)
   }
   keycloakPromise.then(keycloak => {
      let initPromise = null;
      if (!keycloak.authenticated) {
        initPromise = keycloak.init({
          onLoad: "check-sso",
          promiseType: 'native',
        });
        initPromise.then(authenticated => {
          store.dispatch({type: CLEAR_ALERT })
          store.getState().auth.afterLogin.forEach(a => a.func())
        })
      }
      store.dispatch({ type: INIT, keycloak: keycloak, initPromise: initPromise })
   })
}

export const TryLoginAgain = () => (<>Try <Button variant="link" onClick={() => store.getState().auth.keycloak.login() }>log in again</Button></>)

export const LoginLogout = () => {
   const keycloak = useSelector(keycloakSelector)
   if (!!keycloak && keycloak.authenticated) {
      return (
         <Button onClick={ () => keycloak.logout() }>Log out</Button>
      )
   } else {
      return (
         <Button onClick={ () => keycloak.login() }>Log in</Button>
      )
   }
}

export const accessName = access => {
   switch (access) {
       case 0: return 'PUBLIC'
       case 1: return 'PROTECTED'
       case 2: return 'PRIVATE'
       default: return access;
   }
}