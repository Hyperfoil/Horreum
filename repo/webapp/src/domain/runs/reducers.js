import * as actionTypes from './actionTypes';
import { List, Map } from 'immutable';
import * as utils from '../../utils'
import { ONLY_MY_OWN } from '../../components/OwnerSelect'

const initialState = {
    byId: undefined,
    byTest: undefined,
    filteredIds: null,
    selectedRoles: ONLY_MY_OWN,
    suggestQuery: [],
    suggestions: []
}
//Takes events and updates the state accordingly
export const reducer = (state = initialState, action) =>{
    switch(action.type){
        case actionTypes.LOADED: {
            if ( !utils.isEmpty(action.runs) ) {
                if (!state.byId) {
                    state.byId = Map({})
                }
                action.runs.forEach(run => {
                    if (run !== undefined) {
                        state.byId = state.byId.set(`${run.id}`, {
                            ...(state.byId.get(`${run.id}`) || {}), ...run
                        })
                    }
                })
            }
            break;
        }
        case actionTypes.TESTID: {
            const byTest = state.byTest || Map({})
            let testMap = byTest.get(action.id,Map({}));
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if ( run !== undefined ){
                        testMap = testMap.set(`${run.id}`, {
                            ...testMap.get(`${run.id}`, {}),
                            ...run
                        })
                    }
                })
            }
            state.byTest = byTest.set(`${action.id}`,testMap)
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
               state.byId = state.byId.set(`${run.id}`, { ...run, token: action.token })
            }
            break
        }
        case actionTypes.UPDATE_ACCESS: {
            let run = state.byId && state.byId.get(`${action.id}`);
            if (run) {
                state.byId = state.byId.set(`${run.id}`, { ...run, owner: action.owner, access: action.access })
            }
            break
        }
        case actionTypes.UPDATE_SCHEMA: {
            let run = state.byId && state.byId.get(`${action.id}`);
            if (run) {
                state.byId = state.byId.set(`${run.id}`, { ...run, schemaUri: action.schemaUri })
            }
            break
        }
        default:
    }
    return state;
}
