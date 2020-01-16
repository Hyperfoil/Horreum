import * as api from './api';
import * as actionTypes from './actionTypes';
import { isFetchingSuggestions, suggestQuery } from './selectors'

const loaded = run =>({
    type: actionTypes.LOADED,
    runs: Array.isArray(run) ? run : [run]
})
const testId = (id,runs,payload) =>({
    type: actionTypes.TESTID,
    id,
    runs
})

export const get = id =>
    dispatch =>
        api.get(id)
        .then(response => {
            dispatch(loaded(response))
        });

export const all = () => {
    return dispatch =>
        api.all()
        .then(response => {
            dispatch(loaded(response))
        });
    }

export const byTest = (id,payload)=>
    dispatch =>
        api.byTest(id,payload)
        .then(response => {
            return dispatch(testId(id,response,payload))
        })

export const filter = (query, matchAll, callback) => {
   return dispatch => {
      if (query == "") {
         dispatch({
            type: actionTypes.FILTERED,
            ids: null
         })
         callback(true)
         return
      }
      api.filter(query, matchAll)
      .then(response => {
         // TODO: for some reason a 400 response is passed here as undefined
         // instead of giving us the full response
         if (response == undefined) {
            callback(false)
         } else {
            dispatch({
               type: actionTypes.FILTERED,
               ids: response
            })
            callback(true)
         }
      })
   }
}

export const suggest = query => dispatch => {
  if (query == "") {
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
        fetchSuggestions(query, dispatch)
     }
  }
}

const fetchSuggestions = (query, dispatch) => {
   console.log(new Date() + " Looking up " + query)
   api.suggest(query).then(response => {
      if (response == undefined) {
        dispatch({
           type: actionTypes.SUGGEST,
           responseReceived: true,
           options: []
        })
      } else {
        console.log(new Date() + " Received for " + query + ": " + response)
        dispatch({
           type: actionTypes.SUGGEST,
           responseReceived: true,
           options: response
        })
      }
      let nextQuery = suggestQuery()
      if (nextQuery != null) {
        fetchSuggestions(nextQuery, dispatch)
      }
  })
}
