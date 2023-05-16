import * as actionTypes from "./actionTypes"
import { Access } from "../../auth"
import { DeleteAction, LoadedAction, UpdateTokenAction, UpdateAccessAction } from "./reducers"
import { Dispatch } from "redux"
import { ThunkDispatch } from "redux-thunk"
import { AddAlertAction, dispatchError } from "../../alerts"
import Api, { Schema } from "../../api"

const loaded = (schema: Schema | Schema[]): LoadedAction => ({
    type: actionTypes.LOADED,
    schemas: Array.isArray(schema) ? schema : [schema],
})

export function getById(id: number) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.schemaServiceGetSchema(id).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "GET_SCHEMA", "Failed to fetch schema")
            }
        )
}

export function add(payload: Schema) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.schemaServiceAdd(payload).then(
            id => {
                dispatch(loaded({ ...payload, id }))
                return id
            },
            error => dispatchError(dispatch, error, "SAVE_SCHEMA", "Failed to save schema")
        ) as Promise<number>
}

export function all() {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.schemaServiceList().then(
            response => dispatch(loaded(response.schemas)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "LIST_SCHEMAS", "Failed to list schemas")
            }
        )
}

export function resetToken(id: number) {
    return (dispatch: Dispatch<UpdateTokenAction | AddAlertAction>) =>
        Api.schemaServiceResetToken(id).then(
            token =>
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id: id,
                    token: token,
                }),
            error => dispatchError(dispatch, error, "RESET_SCHEMA_TOKEN", "Failed to reset schema token")
        )
}

export function dropToken(id: number) {
    return (dispatch: Dispatch<UpdateTokenAction | AddAlertAction>) =>
        Api.schemaServiceDropToken(id).then(
            () =>
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id: id,
                    token: undefined,
                }),
            error => dispatchError(dispatch, error, "DROP_SCHEMA_TOKEN", "Failed to drop schema token")
        )
}

export function updateAccess(id: number, owner: string, access: Access) {
    return (dispatch: Dispatch<UpdateAccessAction | AddAlertAction>) =>
        Api.schemaServiceUpdateAccess(id, access, owner).then(
            () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access }),
            error => dispatchError(dispatch, error, "SCHEMA_UPDATE", "Failed to update schema access.")
        )
}

export function deleteSchema(id: number) {
    return (dispatch: ThunkDispatch<any, unknown, DeleteAction>) =>
        Api.schemaServiceDelete(id).then(
            () => {
                dispatch({
                    type: actionTypes.DELETE,
                    id: id,
                })
            },
            error => dispatchError(dispatch, error, "SCHEMA_DELETE", "Failed to delete schema " + id)
        )
}
