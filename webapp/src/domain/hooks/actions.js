import * as api from './api';
import * as actionTypes from './actionTypes';

const loaded = hook =>({
    type: actionTypes.LOADED,
    hooks: Array.isArray(hook) ? hook : [hook]
})
export const get = id =>
    dispatch =>
        api.get(id)
        .then(response => {
            dispatch(loaded(response))
        }
);

export const all = () => {
    return dispatch =>
        api.all()
        .then(response => {
            dispatch(loaded(response))
        });
}

