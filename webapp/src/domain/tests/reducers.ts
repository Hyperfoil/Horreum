import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";
import { Access } from "../../auth"
import { ThunkDispatch } from 'redux-thunk';

export interface ViewComponent {
    headerName: string,
    accessors: string,
    render: string | Function | undefined,
    headerOrder: number,
}

export interface View {
    name: string,
    components: ViewComponent[]
}

export interface Test {
    id: number,
    name: string,
    description: string,
    compareUrl: string | Function | undefined,
    owner: string,
    access: Access,
    token: string | null,
    defaultView?: View,
    count?: number, // run count in AllTests
    watching?: string[]
}

export class TestsState {
    byId?: Map<number, Test> = undefined
    loading: boolean = false
    // we need to store watches independently as the information
    // can arrive before the actual test list
    watches: Map<number, string[] | undefined> = Map<number, string[] | undefined>()
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

export interface UpdateTestWatchAction {
    type: typeof actionTypes.UPDATE_TEST_WATCH,
    byId: Map<number, string[] | undefined>,
}

export interface UpdateViewAction {
    type: typeof actionTypes.UPDATE_VIEW,
    testId: number,
    view: View,
}

export type TestAction = LoadingAction | LoadedAction | DeleteAction | UpdateTokenAction | UpdateAccessAction | UpdateTestWatchAction | UpdateViewAction

export type TestDispatch = ThunkDispatch<any, unknown, TestAction>

export const reducer = (state = new TestsState(), action: TestAction) => {
    switch (action.type) {
        case actionTypes.LOADING:
            state.loading = true
        break;
        case actionTypes.LOADED:
            state.loading = false
            if (!state.byId) {
                state.byId = Map<number, Test>()
            }
            if (!utils.isEmpty(action.tests)) {
                action.tests.forEach(test => {
                    if (test && test.id !== null && typeof test.id !== "undefined") {
                        const byId = state.byId as Map<number, Test>
                        state.byId = byId.set(test.id, {
                            ...(byId.get(test.id, {})), ...test
                        })
                    }
                })
            }
        break;
        case actionTypes.UPDATE_TOKEN: {
            let test = state.byId?.get(action.id)
            if (test) {
               state.byId = state.byId?.set(action.id, { ...test, token: action.token })
            }
        }
        break;
        case actionTypes.UPDATE_ACCESS: {
            let test = state.byId?.get(action.id)
            if (test) {
               state.byId = state.byId?.set(action.id, { ...test, owner: action.owner, access: action.access })
            }
        }
        break;
        case actionTypes.DELETE: {
            state.byId = state.byId?.delete(action.id)
        }
        break;
        case actionTypes.UPDATE_TEST_WATCH: {
            state.watches = state.watches.merge(action.byId)
        }
        break;
        case actionTypes.UPDATE_VIEW: {
            let test = state.byId?.get(action.testId)
            if (test) {
               state.byId = state.byId?.set(action.testId, { ...test, defaultView: action.view })
            }
        }
        default:
    }
    return state;
}
