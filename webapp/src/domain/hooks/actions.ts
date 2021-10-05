import * as api from "./api"
import * as actionTypes from "./actionTypes"
import { Hook, LoadedAction, DeleteAction } from "./reducers"
import { Dispatch } from "react"

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

export const getHook = (id: number) => (dispatch: Dispatch<LoadedAction>) =>
    api.getHook(id).then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )

export const addHook = (payload: Hook) => (dispatch: Dispatch<LoadedAction>) =>
    api.addHook(payload).then(
        response => dispatch(loaded(response)),
        rejected => {
            /* noop */
        }
    )

export const allHooks = () => (dispatch: Dispatch<LoadedAction>) =>
    api.allHooks().then(response => dispatch(loaded(response)))

export const removeHook = (id: number) => (dispatch: Dispatch<DeleteAction>) =>
    api.removeHook(id).then(_ => dispatch(removed(id)))
