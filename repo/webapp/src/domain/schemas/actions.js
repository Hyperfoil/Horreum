import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName } from '../../auth.js'

const loaded = schema => {
    if (typeof schema === "undefined") {
        return errored("failed to create hook")
    } else {
        return {
            type: actionTypes.LOADED,
            schemas: Array.isArray(schema) ? schema : [schema]
        }
    }
}
const errored = (message) => ({
    type: actionTypes.ERROR,
    message
})
export const getById = id =>
    dispatch =>
        api.getById(id)
            .then(response => {
                dispatch(loaded(response))
            },rejected =>{

            })
export const getByName = name =>
    dispatch =>
        api.getByName(name)
            .then(response => {
                dispatch(loaded(response))
            }
            );
export const add = (payload) =>
    dispatch =>
        api.add(payload)
            .then(response => {
                dispatch(loaded(response))
            }, rejected => {

            })

export const all = () => {
    return dispatch =>
        api.all()
            .then(response => {
                dispatch(loaded(response))
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
