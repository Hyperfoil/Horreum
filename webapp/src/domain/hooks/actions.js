import * as api from './api';
import * as actionTypes from './actionTypes';

const loaded = hook => {
    if (typeof hook === "undefined") {
        return errored("failed to create hook")
    } else {
        return {
            type: actionTypes.LOADED,
            hooks: Array.isArray(hook) ? hook : [hook]
        }
    }
}
const errored = (message) => ({
    type: actionTypes.ERROR,
    message
})
const removed = id => ({
    type: actionTypes.DELETE,
    id
})
export const get = id =>
    dispatch =>
        api.get(id)
            .then(response => {
                dispatch(loaded(response))
            }
            );

export const add = (payload) =>
    dispatch =>
        api.add(payload)
            .then(response => {
                dispatch(loaded(response))
            },rejected => {
            })

export const all = () => {
    return dispatch =>
        api.all()
            .then(response => {
                dispatch(loaded(response))
            });
}
export const remove = (id) => {
    return dispatch =>
        api.remove(id)
            .then(response => {
                dispatch(removed(id))
            })
}

