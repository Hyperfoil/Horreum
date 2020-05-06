import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName } from '../../auth.js'

const loaded = tests =>({
    type: actionTypes.LOADED,
    tests: Array.isArray(tests) ? tests: [tests]
})
export const fetchSummary = () => {
    return dispatch => {
        api.summary()
        .then(response=>{
            dispatch(loaded(response))
        })
    }
};
export const fetchTest = (id) => {
    return dispatch =>
        api.get(id)
        .then(response=>{
            dispatch(loaded(response))
        })
}
export const sendTest = (test) => {
    return dispatch =>
        api.send(test)
        .then(response=>{
            dispatch(loaded(response));
        })
}

export const resetToken = (id) => {
   return dispatch =>
      api.resetToken(id)
         .then(token => {
            dispatch({
               type: actionTypes.UPDATE_TOKEN,
               id: id,
               token: token,
            });
        })
}

export const dropToken = (id) => {
   return dispatch =>
      api.dropToken(id)
         .then(() => {
            dispatch({
               type: actionTypes.UPDATE_TOKEN,
               id: id,
               token: null,
            });
        })
}

export const updateAccess = (id, owner, access) => {
   return dispatch =>
      api.updateAccess(id, owner, accessName(access))
         .then(() => {
            dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access });
        })
}

export const deleteTest = id => dispatch => api.deleteTest(id)
   .then(() => dispatch({ type: actionTypes.DELETE, id }))
