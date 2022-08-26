import { ThunkDispatch } from "redux-thunk"

import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import * as utils from "../../utils"
import { AddAlertAction } from "../../alerts"
import { Action } from "../../api"

export const EXPERIMENT_RESULT_NEW = "experiment_result/new"
export const globalEventTypes = ["test/new", "run/new", "change/new", EXPERIMENT_RESULT_NEW]
export const testEventTypes = ["run/new", "change/new", EXPERIMENT_RESULT_NEW]

export class ActionsState {
    byId?: Map<string, Action> = undefined
}

export interface LoadedAction {
    type: typeof actionTypes.LOADED
    actions: Action[]
}

export interface DeleteAction {
    type: typeof actionTypes.DELETE
    id: number
}

type ActionsAction = LoadedAction | DeleteAction
export type ActionsDispatch = ThunkDispatch<any, unknown, ActionsAction | AddAlertAction>

export const reducer = (state = new ActionsState(), action: ActionsAction) => {
    switch (action.type) {
        case actionTypes.LOADED:
            if (!state.byId) {
                state.byId = Map({})
            }
            if (!utils.isEmpty(action.actions)) {
                action.actions.forEach(action => {
                    const byId = state.byId as Map<string, Action>
                    state.byId = byId.set(`${action.id}`, {
                        ...(byId.get(`${action.id}`) || {}),
                        ...action,
                    })
                })
            }
            break
        case actionTypes.DELETE:
            {
                const byId = state.byId || Map({})
                if (byId.has(`${action.id}`)) {
                    state.byId = byId.delete(`${action.id}`)
                }
            }
            break
        default:
    }
    return state
}
