import React from 'react';
import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import keycloakConfig from './keycloakConfig.js'

import {
  Alert,
  AlertActionCloseButton,
  Button,
} from '@patternfly/react-core';

import Keycloak from "keycloak-js"

import store from './store'

const INIT = "auth/INIT"
const AUTHENTICATED = "auth/AUTHENTICATED"
export const REQUEST_FAILED = "auth/REQUEST_FAILED"
const REGISTER_AFTER_LOGIN = "auth/REGISTER_AFTER_LOGIN"

const initialState = {
  keycloak: null,
  initPromise: null,
  insufficientPermissions: false,
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
      case AUTHENTICATED:
         state.insufficientPermissions = false
         break;
      case REQUEST_FAILED:
         state.insufficientPermissions = action.value == undefined ? true : action.value
         break;
      case REGISTER_AFTER_LOGIN:
         state.afterLogin = [...state.afterLogin.filter(({ name, func }) => name != action.name),
                             { name: action.name, func: action.func }]
         break;
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

const keycloakSelector = () => {
   return store.getState().auth.keycloak;
}

const insufficientPermissionsSelector = () => {
   return store.getState().auth.insufficientPermissions
}

export const roleToName = (role) => {
   return role ? (role.charAt(0).toUpperCase() + role.slice(1, -5)) : null
}

export const isAuthenticatedSelector = () => {
   let keycloak = store.getState().auth.keycloak;
   return !!keycloak && keycloak.authenticated;
}

export const isUploaderSelector = () => {
   return store.getState().auth.keycloak.hasRealmRole("uploader")
}

export const isTesterSelector = () => {
   return store.getState().auth.keycloak.hasRealmRole("tester")
}

export const isAdminSelector = () => {
   return store.getState().auth.keycloak.hasRealmRole("admin")
}

export const rolesSelector = () => {
   let keycloak = store.getState().auth.keycloak;
   return keycloak && keycloak.realmAccess ? keycloak.realmAccess.roles : []
}

export const defaultRoleSelector = () => {
   let teamRoles = rolesSelector().filter(r => r.endsWith("-team")).sort()
   return teamRoles.length > 0 ? teamRoles[0] : null;
}

export const initKeycloak = () => {
   let keycloak = keycloakSelector();
   if (keycloak === null) {
     keycloak = Keycloak(keycloakConfig)
   }
   let initPromise = null;
   if (!keycloak.authenticated) {
     initPromise = keycloak.init({
       promiseType: 'native',
     });
     initPromise.then(authenticated => {
       store.dispatch({type: AUTHENTICATED })
       store.getState().auth.afterLogin.forEach(a => a.func())
     })
   }
   store.dispatch({ type: INIT, keycloak: keycloak, initPromise: initPromise })
}

export const RequestForbiddenAlert = () => {
   const insufficientPermissions = useSelector(insufficientPermissionsSelector)
   const dispatch = useDispatch();
   if (insufficientPermissions) {
      return (
         <Alert variant="warning" title="Request failed due to insufficient permissions"
                action={<AlertActionCloseButton onClose={() => {
              dispatch({ type: REQUEST_FAILED, value: false})
           }} />} >
            Try <a href="/">log in again</a>.
         </Alert>
      )
   }
   return "";
}

export const LoginLogout = () => {
   const keycloak = useSelector(keycloakSelector)
   if (keycloak.authenticated) {
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