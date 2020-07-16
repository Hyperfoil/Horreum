import * as api from './api';
import * as actionTypes from './actionTypes';
import { Hook, LoadedAction, DeleteAction } from './reducers';
import { Dispatch } from 'react';

const loaded = (hook: Hook | Hook[]): LoadedAction => {
    return {
        type: actionTypes.LOADED,
        hooks: Array.isArray(hook) ? hook : [hook]
    }
}
const removed = (id: number): DeleteAction => ({
    type: actionTypes.DELETE,
    id
})

export const get = (id: number) => (dispatch: Dispatch<LoadedAction>) =>
    api.get(id).then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )

export const add = (payload: Hook) => (dispatch: Dispatch<LoadedAction>) =>
    api.add(payload).then(
        response => dispatch(loaded(response)),
        rejected => {}
    )

export const all = () => (dispatch: Dispatch<LoadedAction>) =>
    api.all().then(
        response => dispatch(loaded(response))
    )

export const remove = (id: number) => (dispatch: Dispatch<DeleteAction>) =>
    api.remove(id).then(
        response => dispatch(removed(id))
    )
