import * as api from './api';
import * as actionTypes from './actionTypes';
import { isFetchingSuggestions, suggestQuery } from './selectors'
import { accessName } from '../../auth'

const loaded = run =>({
    type: actionTypes.LOADED,
    runs: Array.isArray(run) ? run : [run]
})
const testId = (id, runs) =>({
    type: actionTypes.TESTID,
    id,
    runs
})

export const get = (id, token) => dispatch =>
   api.get(id, token).then(
      response => dispatch(loaded(response)),
      error => dispatch(loaded([]))
   )

export const all = () => dispatch => {
   dispatch({ type: actionTypes.LOADING })
   api.all().then(
      response => dispatch(loaded(response)),
      error => dispatch(loaded([]))
   )
}

export const byTest = (id, payload, roles) => dispatch => {
   dispatch({ type: actionTypes.LOADING })
   api.byTest(id, payload, roles).then(
      response => dispatch(testId(id,response)),
      error => dispatch(testId(id, []))
   )
}

export const filter = (query, matchAll, roles, callback) => dispatch => {
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

export const suggest = (query, roles) => dispatch => {
  if (query === "") {
     dispatch({
        type: actionTypes.SUGGEST,
        responseReceived: false,
        options: [],
     })
  } else {
     let fetching = isFetchingSuggestions()
     dispatch({
        type: actionTypes.LOAD_SUGGESTIONS,
        query: query
     })
     if (!fetching) {
        fetchSuggestions(query, roles, dispatch)
     }
  }
}

const fetchSuggestions = (query, roles, dispatch) => {
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
      let nextQuery = suggestQuery()
      if (nextQuery != null) {
         fetchSuggestions(nextQuery, dispatch)
      }
   })
}

export const selectRoles = (selection) => {
   return {
      type: actionTypes.SELECT_ROLES,
      selection: selection,
   }
}

export const resetToken = (id) => dispatch => {
   return api.resetToken(id).then(token => {
      dispatch({
         type: actionTypes.UPDATE_TOKEN,
         id: id,
         token: token,
      })
   })
}

export const dropToken = (id) => dispatch => {
   return api.dropToken(id).then(response => {
      dispatch({
         type: actionTypes.UPDATE_TOKEN,
         id: id,
         token: null,
      })
   })
}

export const updateAccess = (id, owner, access) => dispatch => {
   return api.updateAccess(id, owner, accessName(access)).then(response => {
      dispatch({
         type: actionTypes.UPDATE_ACCESS,
         id: id,
         owner: owner,
         access: access,
      })
   })
}
