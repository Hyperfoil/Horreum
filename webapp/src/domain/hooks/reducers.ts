import { ThunkDispatch } from "redux-thunk"

import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import * as utils from "../../utils"
import { AddAlertAction } from "../../alerts"
import { Hook } from "../../api"

export const globalEventTypes = ["test/new", "run/new", "change/new"]
export const testHookEventTypes = ["run/new", "change/new", "experiment_result/new"]

export class HooksState {
    byId?: Map<string, Hook> = undefined
}

export interface LoadedAction {
    type: typeof actionTypes.LOADED
    hooks: Hook[]
}

export interface DeleteAction {
    type: typeof actionTypes.DELETE
    id: number
}

type HooksAction = LoadedAction | DeleteAction
export type HooksDispatch = ThunkDispatch<any, unknown, HooksAction | AddAlertAction>

export const reducer = (state = new HooksState(), action: HooksAction) => {
    switch (action.type) {
        case actionTypes.LOADED:
            if (!state.byId) {
                state.byId = Map({})
            }
            if (!utils.isEmpty(action.hooks)) {
                action.hooks.forEach(hook => {
                    const byId = state.byId as Map<string, Hook>
                    state.byId = byId.set(`${hook.id}`, {
                        ...(byId.get(`${hook.id}`) || {}),
                        ...hook,
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
