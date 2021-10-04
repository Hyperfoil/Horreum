import * as actionTypes from "./actionTypes"
import { Map } from "immutable"
import * as utils from "../../utils"
import { Access } from "../../auth"
import { Action } from "redux"
import { ThunkDispatch } from "redux-thunk"

export interface Schema {
    id: number
    name: string
    description: string
    uri: string
    schema: object
    owner: string
    access: Access
    token: string | null
}

export class SchemasState {
    byId?: Map<string, Schema> = undefined
}

export interface LoadedAction extends Action {
    type: typeof actionTypes.LOADED
    schemas: Schema[]
}

export interface DeleteAction extends Action {
    type: typeof actionTypes.DELETE
    id: number
}

export interface UpdateTokenAction extends Action {
    type: typeof actionTypes.UPDATE_TOKEN
    id: number
    token: string | null
}

export interface UpdateAccessAction extends Action {
    type: typeof actionTypes.UPDATE_ACCESS
    id: number
    owner: string
    access: Access
}

export type SchemaAction = LoadedAction | DeleteAction | UpdateTokenAction | UpdateAccessAction

export type SchemaDispatch = ThunkDispatch<any, unknown, SchemaAction>

export const reducer = (state = new SchemasState(), action: SchemaAction) => {
    switch (action.type) {
        case actionTypes.LOADED:
            if (!state.byId) {
                state.byId = Map({})
            }
            if (!utils.isEmpty(action.schemas)) {
                state.byId = state.byId.clear()
                action.schemas.forEach(schema => {
                    const byId = state.byId as Map<string, Schema>
                    state.byId = byId.set(`${schema.id}`, {
                        ...(byId.get(`${schema.id}`) || {}),
                        ...schema,
                    })
                })
            }
            break
        case actionTypes.DELETE:
            if (state.byId && state.byId.has(`${action.id}`)) {
                state.byId = state.byId.delete(`${action.id}`)
            }
            break
        case actionTypes.UPDATE_TOKEN:
            {
                let schema = state.byId?.get(`${action.id}`)
                if (schema) {
                    state.byId = (state.byId as Map<string, Schema>).set(`${action.id}`, {
                        ...schema,
                        token: action.token,
                    })
                }
            }
            break
        case actionTypes.UPDATE_ACCESS:
            {
                let schema = state.byId && state.byId.get(`${action.id}`)
                if (schema) {
                    state.byId = (state.byId as Map<string, Schema>).set(`${action.id}`, {
                        ...schema,
                        owner: action.owner,
                        access: action.access,
                    })
                }
            }
            break
        default:
    }
    return state
}
