import { Dispatch } from 'react';
import { Access } from '../../auth';
import { Role } from '../../components/OwnerSelect';
import { FilteredAction, LoadedAction, LoadingAction, LoadSuggestionsAction, Run, SelectRolesAction, SuggestAction, TestIdAction, UpdateAccessAction, UpdateTokenAction, TrashAction } from '../runs/reducers';
import * as actionTypes from './actionTypes';
import * as api from './api';
import { isFetchingSuggestions, suggestQuery } from './selectors';
import store from '../../store'

const loaded = (run: Run | Run[]): LoadedAction =>({
    type: actionTypes.LOADED,
    runs: Array.isArray(run) ? run : [run]
})

const testId = (id: number, runs: Run[]): TestIdAction =>({
    type: actionTypes.TESTID,
    id,
    runs
})

export const get = (id: number, token?: string) => (dispatch: Dispatch<LoadedAction>) =>
   api.get(id, token).then(
      response => dispatch(loaded(response)),
      error => dispatch(loaded([]))
   )

export const all = (trashed?: boolean) => (dispatch: Dispatch<LoadingAction | LoadedAction>) => {
   dispatch({ type: actionTypes.LOADING })
   api.all(trashed).then(
      response => dispatch(loaded(response)),
      error => dispatch(loaded([]))
   )
}

export const byTest = (id: number, trashed?: boolean) => (dispatch: Dispatch<LoadingAction | TestIdAction>) => {
   dispatch({ type: actionTypes.LOADING })
   api.byTest(id, trashed).then(
      response => dispatch(testId(id,response)),
      error => dispatch(testId(id, []))
   )
}

export const filter = (query: string, matchAll: boolean, roles: string, callback: (success: boolean) => void) => (dispatch: Dispatch<FilteredAction>) => {
   if (query === "" && roles === "__all") {
      dispatch({
         type: actionTypes.FILTERED,
         ids: null
      })
      callback(true)
      return
   }
   api.filter(query, matchAll, roles)
   .then(response => {
      dispatch({
         type: actionTypes.FILTERED,
         ids: response
      })
      callback(true)
   }, e => callback(false))
}

export const suggest = (query: string, roles: string) => (dispatch: Dispatch<SuggestAction | LoadSuggestionsAction>) => {
  if (query === "") {
     dispatch({
        type: actionTypes.SUGGEST,
        responseReceived: false,
        options: [],
     })
  } else {
     let fetching = isFetchingSuggestions(store.getState())
     dispatch({
        type: actionTypes.LOAD_SUGGESTIONS,
        query: query
     })
     if (!fetching) {
        fetchSuggestions(query, roles, dispatch)
     }
  }
}

const fetchSuggestions = (query: string, roles: string, dispatch: Dispatch<SuggestAction | LoadSuggestionsAction>) => {
   api.suggest(query, roles).then(response => {
      dispatch({
         type: actionTypes.SUGGEST,
         responseReceived: true,
         options: response
      })
   }, e => {
      dispatch({
         type: actionTypes.SUGGEST,
         responseReceived: true,
         options: []
      })
   }).finally(() => {
      let nextQuery = suggestQuery(store.getState())
      if (nextQuery != null) {
         fetchSuggestions(nextQuery, roles, dispatch)
      }
   })
}

export const selectRoles = (selection: Role): SelectRolesAction => {
   return {
      type: actionTypes.SELECT_ROLES,
      selection: selection,
   }
}

export const resetToken = (id: number, testid: number) => (dispatch: Dispatch<UpdateTokenAction>) => {
   return api.resetToken(id).then(token => {
      dispatch({
         type: actionTypes.UPDATE_TOKEN,
         id,
         testid,
         token,
      })
   })
}

export const dropToken = (id: number, testid: number) => (dispatch: Dispatch<UpdateTokenAction>) => {
   return api.dropToken(id).then(response => {
      dispatch({
         type: actionTypes.UPDATE_TOKEN,
         id,
         testid,
         token: null,
      })
   })
}

export const updateAccess = (id: number, testid: number, owner: string, access: Access) => (dispatch: Dispatch<UpdateAccessAction>) => {
   return api.updateAccess(id, owner, access).then(response => {
      dispatch({
         type: actionTypes.UPDATE_ACCESS,
         id,
         testid,
         owner,
         access,
      })
   })
}

export const trash = (id: number, testid: number, isTrashed: boolean = true) => (dispatch: Dispatch<TrashAction>) =>
   api.trash(id, isTrashed).then(
      response => {
         console.log(response)
         dispatch({
            type: actionTypes.TRASH,
            id,
            testid,
            isTrashed,
         })
      }
   )