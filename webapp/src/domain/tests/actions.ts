import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName, Access } from '../../auth'
import { Test, LoadingAction, LoadedAction, UpdateTokenAction, UpdateAccessAction, DeleteAction } from './reducers';
import { Dispatch } from 'react';

const loaded = (tests: Test | Test[]): LoadedAction =>({
    type: actionTypes.LOADED,
    tests: Array.isArray(tests) ? tests: [tests]
})

export const fetchSummary = () => (dispatch: Dispatch<LoadingAction | LoadedAction>) => {
    dispatch({ type: actionTypes.LOADING })
    api.summary().then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )
}

export const fetchTest = (id: number) => (dispatch: Dispatch<LoadedAction>) =>
    api.get(id).then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )

export const sendTest = (test: Test) => (dispatch: Dispatch<LoadedAction>) =>
    api.send(test).then(
        response => dispatch(loaded(response))
    )

export const resetToken = (id: number) => (dispatch: Dispatch<UpdateTokenAction>) =>
    api.resetToken(id).then(
        token => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: token,
        })
    )

export const dropToken = (id: number) => (dispatch: Dispatch<UpdateTokenAction>) =>
    api.dropToken(id).then(
        () => dispatch({
               type: actionTypes.UPDATE_TOKEN,
               id: id,
               token: null,
        })
    )

export const updateAccess = (id: number, owner: string, access: Access) => (dispatch: Dispatch<UpdateAccessAction>) =>
    api.updateAccess(id, owner, accessName(access)).then(
        () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access })
    )

export const deleteTest = (id: number) => (dispatch: Dispatch<DeleteAction>) =>
    api.deleteTest(id).then(
        () => dispatch({ type: actionTypes.DELETE, id })
    )
