import * as actionTypes from './actionTypes';
import { List, Map } from 'immutable';
import * as utils from '../../utils'
import { Role } from '../../components/OwnerSelect'
import Run from './Run';
import { Access } from "../../auth"
import { ThunkDispatch } from 'redux-thunk';

export interface Run {
    id: number,
    testid: number,
    start: number,
    stop: number,
    description: string,
    owner: string,
    access: Access,
    token: string | null,
    data: any,
    testname?: string,
    schema?: Map<string, number>
    // TODO - this could rather be a map<view_id, viewcomponent[]>
    view?: any[]
    trashed: boolean,
}

export class RunsState {
    loading: boolean = false
    byId?: Map<number, Run> = undefined
    byTest?: Map<number, Map<number, Run>> = undefined
    currentPage: number[] = []
    currentTotal: number = 0
    selectedRoles?: Role = undefined
    suggestQuery: string[] = []
    suggestions: string[] = []
}

export interface LoadingAction {
    type: typeof actionTypes.LOADING,
}

export interface LoadedAction {
    type: typeof actionTypes.LOADED,
    runs: Run[],
    total?: number,
}

export interface TestIdAction {
    type: typeof actionTypes.TESTID,
    id: number,
    runs: Run[],
    total: number,
}

export interface LoadSuggestionsAction {
    type: typeof actionTypes.LOAD_SUGGESTIONS,
    query: string,
}

export interface SuggestAction {
    type: typeof actionTypes.SUGGEST,
    responseReceived: boolean,
    options: string[],
}

export interface SelectRolesAction {
    type: typeof actionTypes.SELECT_ROLES,
    selection: Role
}

export interface UpdateTokenAction {
    type: typeof actionTypes.UPDATE_TOKEN,
    id: number,
    testid: number,
    token: string | null,
}

export interface UpdateAccessAction {
    type: typeof actionTypes.UPDATE_ACCESS,
    id: number,
    testid: number,
    owner: string,
    access: Access,
}

export interface TrashAction {
    type: typeof actionTypes.TRASH,
    id: number,
    testid: number,
    isTrashed: boolean,
}

export interface UpdateDescriptionAction {
    type: typeof actionTypes.UPDATE_DESCRIPTION,
    id: number,
    testid: number,
    description: string,
}

type RunsAction = LoadingAction | LoadedAction | TestIdAction |
                  LoadSuggestionsAction |  SuggestAction | SelectRolesAction |
                  UpdateTokenAction | UpdateAccessAction | TrashAction | UpdateDescriptionAction

export type RunsDispatch = ThunkDispatch<any, unknown, RunsAction>

//Takes events and updates the state accordingly
export const reducer = (state = new RunsState(), action: RunsAction) =>{
    switch(action.type) {
        case actionTypes.LOADING:
            state.loading = true
        break;
        case actionTypes.LOADED: {
            state.loading = false
            if (!state.byId) {
                state.byId = Map<number, Run>()
            }
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if (run !== undefined) {
                        const byId = state.byId as Map<number, Run>
                        state.byId = byId.set(run.id, {
                            ...(byId.get(run.id) || {}), ...run
                        })
                    }
                })
            }
            state.currentPage = action.runs.map(run => run.id)
            if (action.total) {
                state.currentTotal = action.total
            }
            break;
        }
        case actionTypes.TESTID: {
            state.loading = false
            const byTest = state.byTest || Map()
            let testMap: Map<number, Run> = byTest.get(action.id, Map<number, Run>())
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if ( run !== undefined ){
                        testMap = testMap.set(run.id, {
                            ...testMap.get(run.id),
                            ...run
                        })
                    }
                })
            }
            state.byTest = byTest.set(action.id, testMap)
            state.currentPage = action.runs.map(run => run.id)
            state.currentTotal = action.total;
            break;
        }
        case actionTypes.LOAD_SUGGESTIONS: {
            if (state.suggestQuery.length === 0) {
               state.suggestQuery = [ action.query ]
            } else {
               state.suggestQuery = [ state.suggestQuery[0], action.query ]
            }
            break;
        }
        case actionTypes.SUGGEST: {
            state.suggestions = action.options
            if (action.responseReceived) {
               state.suggestQuery.shift()
            }
            break;
        }
        case actionTypes.SELECT_ROLES: {
            state.selectedRoles = action.selection
            break
        }
        case actionTypes.UPDATE_TOKEN: {
            state = updateRun(state, action.id, action.testid, { token: action.token })
            break
        }
        case actionTypes.UPDATE_ACCESS: {
            state = updateRun(state, action.id, action.testid, { owner: action.owner, access: action.access })
            break
        }
        case actionTypes.TRASH: {
            state = updateRun(state, action.id, action.testid, { trashed: action.isTrashed })
            break
        }
        case actionTypes.UPDATE_DESCRIPTION: {
            state = updateRun(state, action.id, action.testid, { description: action.description })
            break
        }
        default:
    }
    return state;
}

function updateRun(state: RunsState, id: number, testid: number, patch: object) {
    let run = state.byId?.get(id);
    if (run) {
        state.byId = (state.byId || Map<number, Run>()).set(run.id, { ...run, ...patch })
    }
    let testMap: Map<number, Run> | undefined = state.byTest?.get(testid)
    if (testMap) {
        const current: Run | undefined = testMap.get(id)
        if (current) {
            testMap = testMap.set(id, { ...current, ...patch })
        }
        state.byTest = state.byTest?.set(testid, testMap)
    }
    return state
}
