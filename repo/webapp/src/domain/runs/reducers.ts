import * as actionTypes from './actionTypes';
import { List, Map } from 'immutable';
import * as utils from '../../utils'
import { Role, ONLY_MY_OWN } from '../../components/OwnerSelect'
import Run from './Run';
import { Access } from "../../auth"

export interface Run {
    id: number,
    start: number,
    owner: string,
    access: Access,
    token: string | null,
}

export class RunsState {
    loading: boolean = false
    byId?: Map<string, Run> = undefined
    byTest?: Map<number, Map<string, Run>> = undefined
    filteredIds: List<string> | null = null
    selectedRoles: Role = ONLY_MY_OWN
    suggestQuery: string[] = []
    suggestions: string[] = []
}

export interface LoadingAction {
    type: typeof actionTypes.LOADING,
}

export interface LoadedAction {
    type: typeof actionTypes.LOADED,
    runs: Run[],
}

export interface TestIdAction {
    type: typeof actionTypes.TESTID,
    id: number,
    runs: Run[],
}

export interface FilteredAction {
    type: typeof actionTypes.FILTERED,
    ids: number[] | null,
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
    token: string | null,
}

export interface UpdateAccessAction {
    type: typeof actionTypes.UPDATE_ACCESS,
    id: number,
    owner: string,
    access: Access,
}

type RunsAction = LoadingAction | LoadedAction | TestIdAction | FilteredAction |
                  LoadSuggestionsAction |  SuggestAction | SelectRolesAction |
                  UpdateTokenAction | UpdateAccessAction

//Takes events and updates the state accordingly
export const reducer = (state = new RunsState(), action: RunsAction) =>{
    switch(action.type) {
        case actionTypes.LOADING:
            state.loading = true
        break;
        case actionTypes.LOADED: {
            state.loading = false
            if (!state.byId) {
                state.byId = Map({})
            }
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if (run !== undefined) {
                        const byId = state.byId as Map<string, Run>
                        state.byId = byId.set(`${run.id}`, {
                            ...(byId.get(`${run.id}`) || {}), ...run
                        })
                    }
                })
            }
            break;
        }
        case actionTypes.TESTID: {
            state.loading = false
            const byTest = state.byTest || Map()
            let testMap: Map<string, Run> = byTest.get(action.id, Map({}));
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if ( run !== undefined ){
                        testMap = testMap.set(`${run.id}`, {
                            ...testMap.get(`${run.id}`),
                            ...run
                        })
                    }
                })
            }
            state.byTest = byTest.set(action.id, testMap)
            break;
        }
        case actionTypes.FILTERED: {
            // The run.ids in LOADED are converted to strings using the backtick notation for whatever reason
            state.filteredIds = action.ids == null ? null : List(action.ids.map(String))
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
            let run = state.byId && state.byId.get(`${action.id}`);
            if (run) {
               state.byId = (state.byId || Map<string, Run>()).set(`${run.id}`, { ...run, token: action.token })
            }
            break
        }
        case actionTypes.UPDATE_ACCESS: {
            let run = state.byId && state.byId.get(`${action.id}`);
            if (run) {
                state.byId = (state.byId || Map<string, Run>()).set(`${run.id}`, { ...run, owner: action.owner, access: action.access })
            }
            break
        }
        default:
    }
    return state;
}
