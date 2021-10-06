import * as api from "./api"
import * as actionTypes from "./actionTypes"
import { Hook, LoadedAction, DeleteAction } from "./reducers"
import { Dispatch } from "redux"
import { AddAlertAction, dispatchError } from "../../alerts"

const loaded = (hook: Hook | Hook[]): LoadedAction => {
    return {
        type: actionTypes.LOADED,
        hooks: Array.isArray(hook) ? hook : [hook],
    }
}
const removed = (id: number): DeleteAction => ({
    type: actionTypes.DELETE,
    id,
})

export function getHook(id: number) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        api.getHook(id).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "GET_HOOK", "Failed to get hook")
            }
        )
}

export function addHook(payload: Hook) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        api.addHook(payload).then(
            response => dispatch(loaded(response)),
            error => dispatchError(dispatch, error, "ADD_HOOK", "Failed to add hook")
        )
}

export function allHooks() {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        api.allHooks().then(
            response => dispatch(loaded(response)),
            error => dispatchError(dispatch, error, "GET_HOOKS", "Failed to get hooks")
        )
}

export function removeHook(id: number) {
    return (dispatch: Dispatch<DeleteAction | AddAlertAction>) =>
        api.removeHook(id).then(
            _ => dispatch(removed(id)),
            error => dispatchError(dispatch, error, "REMOVE_HOOK", "Failed to remove hook")
        )
}
