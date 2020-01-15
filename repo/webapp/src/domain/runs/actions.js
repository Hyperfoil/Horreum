import * as api from './api';
import * as actionTypes from './actionTypes';

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

