import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName } from '../../auth.js'

const loaded = tests =>({
    type: actionTypes.LOADED,
    tests: Array.isArray(tests) ? tests: [tests]
})

export const fetchSummary = () => dispatch => {
    dispatch({ type: actionTypes.LOADING })
    api.summary().then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )
}

export const fetchTest = (id) => dispatch =>
    api.get(id).then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )

export const sendTest = (test) => dispatch =>
    api.send(test).then(
        response => dispatch(loaded(response))
    )

export const resetToken = (id) => dispatch =>
    api.resetToken(id).then(
        token => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: token,
        })
    )

export const dropToken = (id) => dispatch =>
    api.dropToken(id).then(
        () => dispatch({
               type: actionTypes.UPDATE_TOKEN,
               id: id,
               token: null,
        })
    )

export const updateAccess = (id, owner, access) => dispatch =>
    api.updateAccess(id, owner, accessName(access)).then(
        () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access })
    )

export const deleteTest = id => dispatch =>
    api.deleteTest(id).then(
        () => dispatch({ type: actionTypes.DELETE, id })
    )
