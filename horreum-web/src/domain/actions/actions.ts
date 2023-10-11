import Api, { Action } from "../../api"
import * as actionTypes from "./actionTypes"
import { LoadedAction, DeleteAction } from "./reducers"
import { Dispatch } from "redux"
import { AddAlertAction, dispatchError } from "../../alerts"

const loaded = (action: Action | Action[]): LoadedAction => {
    return {
        type: actionTypes.LOADED,
        actions: Array.isArray(action) ? action : [action],
    }
}
const removed = (id: number): DeleteAction => ({
    type: actionTypes.DELETE,
    id,
})

export function getAction(id: number) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.actionServiceGet(id).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "GET_ACTION", "Failed to get action")
            }
        )
}

export function addAction(action: Action) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.actionServiceAdd(action).then(
            response => dispatch(loaded(response)),
            error => dispatchError(dispatch, error, "ADD_ACTION", "Failed to add action")
        )
}

export function allActions() {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.actionServiceList().then(
            response => dispatch(loaded(response)),
            error => dispatchError(dispatch, error, "GET_ACTIONS", "Failed to get actions")
        )
}

export function removeAction(id: number) {
    return (dispatch: Dispatch<DeleteAction | AddAlertAction>) =>
        Api.actionServiceDelete(id).then(
            _ => dispatch(removed(id)),
            error => dispatchError(dispatch, error, "REMOVE_ACTION", "Failed to remove action")
        )
}
