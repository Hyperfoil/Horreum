import Api, { Hook } from "../../api"
import * as actionTypes from "./actionTypes"
import { LoadedAction, DeleteAction } from "./reducers"
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
        Api.hookServiceGet(id).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "GET_HOOK", "Failed to get hook")
            }
        )
}

export function addHook(hook: Hook) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.hookServiceAdd(hook).then(
            response => dispatch(loaded(response)),
            error => dispatchError(dispatch, error, "ADD_HOOK", "Failed to add hook")
        )
}

export function allHooks() {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.hookServiceList().then(
            response => dispatch(loaded(response)),
            error => dispatchError(dispatch, error, "GET_HOOKS", "Failed to get hooks")
        )
}

export function removeHook(id: number) {
    return (dispatch: Dispatch<DeleteAction | AddAlertAction>) =>
        Api.hookServiceDelete(id).then(
            _ => dispatch(removed(id)),
            error => dispatchError(dispatch, error, "REMOVE_HOOK", "Failed to remove hook")
        )
}
