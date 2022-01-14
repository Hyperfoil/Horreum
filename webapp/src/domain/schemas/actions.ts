import * as api from "./api"
import * as actionTypes from "./actionTypes"
import { Access } from "../../auth"
import { Schema, DeleteAction, LoadedAction, UpdateTokenAction, UpdateAccessAction } from "./reducers"
import { Dispatch } from "redux"
import { ThunkDispatch } from "redux-thunk"
import { AddAlertAction, dispatchError } from "../../alerts"

const loaded = (schema: Schema | Schema[]): LoadedAction => ({
    type: actionTypes.LOADED,
    schemas: Array.isArray(schema) ? schema : [schema],
})

export function getById(id: number) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        api.getById(id).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "GET_SCHEMA", "Failed to fetch schema")
            }
        )
}

export function add(payload: Schema) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        api.add(payload).then(
            id => {
                dispatch(loaded({ ...payload, id }))
                return id
            },
            error => dispatchError(dispatch, error, "ADD_SCHEMA", "Failed to add schema")
        )
}

export function all() {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        api.all().then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(loaded([]))
                return dispatchError(dispatch, error, "LIST_SCHEMAS", "Failed to list schemas")
            }
        )
}

export function resetToken(id: number) {
    return (dispatch: Dispatch<UpdateTokenAction | AddAlertAction>) =>
        api.resetToken(id).then(
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
        api.dropToken(id).then(
            () =>
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id: id,
                    token: null,
                }),
            error => dispatchError(dispatch, error, "DROP_SCHEMA_TOKEN", "Failed to drop schema token")
        )
}

export function updateAccess(id: number, owner: string, access: Access) {
    return (dispatch: Dispatch<UpdateAccessAction | AddAlertAction>) =>
        api.updateAccess(id, owner, access).then(
            () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access }),
            error => dispatchError(dispatch, error, "SCHEMA_UPDATE", "Failed to update schema access.")
        )
}

export function deleteSchema(id: number) {
    return (dispatch: ThunkDispatch<any, unknown, DeleteAction>) =>
        api.deleteSchema(id).then(
            () => {
                dispatch({
                    type: actionTypes.DELETE,
                    id: id,
                })
            },
            error => dispatchError(dispatch, error, "SCHEMA_DELETE", "Failed to delete schema " + id)
        )
}

export function listExtractors(schemaId?: number) {
    return (dispatch: ThunkDispatch<any, unknown, DeleteAction>) =>
        api
            .listExtractors(schemaId)
            .catch(error =>
                dispatchError(
                    dispatch,
                    error,
                    "LIST_EXTRACTORS",
                    "Failed to list extractors" + (schemaId !== undefined ? "for schema " + schemaId : "")
                )
            )
}
