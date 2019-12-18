import * as api from './api';
import * as actionTypes from './actionTypes';

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
