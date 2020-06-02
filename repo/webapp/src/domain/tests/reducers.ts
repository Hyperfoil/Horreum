import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";
import { Access } from "../../auth"

export interface Test {
    id: number,
    owner: string,
    access: Access,
    token: string | null,
}

export class TestsState {
    byId?: Map<string, Test> = undefined
    loading: boolean = false
}

export interface LoadingAction {
    type: typeof actionTypes.LOADING,
}

export interface LoadedAction {
    type: typeof actionTypes.LOADED,
    tests: Test[],
}

export interface DeleteAction {
    type: typeof actionTypes.DELETE,
    id: number,
}

export interface UpdateTokenAction {
    type: typeof actionTypes.UPDATE_TOKEN,
    id: number,
    token: string | null,
}

export interface UpdateAccessAction {
    type: typeof actionTypes.UPDATE_ACCESS,
    id: number,
    owner: string,
    access: Access,
}

type TestAction = LoadingAction | LoadedAction | DeleteAction | UpdateTokenAction | UpdateAccessAction

export const reducer = (state = new TestsState(), action: TestAction) => {
    switch (action.type) {
        case actionTypes.LOADING:
            state.loading = true
        break;
        case actionTypes.LOADED:
            state.loading = false
            if (!state.byId) {
                state.byId = Map({})
            }
            if (!utils.isEmpty(action.tests)) {
                action.tests.forEach(test => {
                    if (test && test.id !== null && typeof test.id !== "undefined") {
                        const byId = state.byId as Map<string, Test>
                        state.byId = byId.set("t" + test.id, {
                            ...(byId.get("t" + test.id, {})), ...test
                        })
                    }
                })
            }
        break;
        case actionTypes.UPDATE_TOKEN: {
            let test = state.byId?.get("t" + action.id)
            if (test) {
               state.byId = (state.byId as Map<string, Test>).set("t" + action.id, { ...test, token: action.token })
            }
        }
        break;
        case actionTypes.UPDATE_ACCESS: {
            let test = state.byId?.get("t" + action.id)
            if (test) {
               state.byId = (state.byId as Map<string, Test>).set("t" + action.id, { ...test, owner: action.owner, access: action.access })
            }
        }
        break;
        case actionTypes.DELETE: {
            if (state.byId) {
               state.byId = (state.byId as Map<string, Test>).delete("t" + action.id)
            }
        }
        break;
        default:
    }
    return state;
}
