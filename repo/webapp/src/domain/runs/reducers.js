import * as actionTypes from './actionTypes';
import { List, Map } from 'immutable';
import * as utils from '../../utils'

const initialState = {
    byId: Map({}),
    byTest: Map({}),
    filteredIds: null,
    suggestQuery: [],
    suggestions: []
}
//Takes events and updates the state accordingly
export const reducer = (state = initialState, action) =>{
    switch(action.type){
        case actionTypes.LOADED: {
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if (run != undefined) {
                        state.byId = state.byId.set(`${run.id}`, {
                            ...(state.byId.get(`${run.id}`) || {}), ...run
                        })
                    }
                })
            }
            break;
        }
        case actionTypes.TESTID: {
            let testMap = state.byTest.get(action.id,Map({}));
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    if ( run != undefined ){
                        testMap = testMap.set(`${run.id}`, {
                            ...testMap.get(`${run.id}`, {}),
                            ...run
                        })
                    }
                })
            }
            state.byTest = state.byTest.set(`${action.id}`,testMap)
            break;
        }
        case actionTypes.FILTERED: {
            // The run.ids in LOADED are converted to strings using the backtick notation for whatever reason
            state.filteredIds = action.ids == null ? null : List(action.ids.map(String))
            break;
        }
        case actionTypes.LOAD_SUGGESTIONS: {
            if (state.suggestQuery.length == 0) {
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
        default:
    }
    return state;
}
