import * as actionTypes from './actionTypes';
import { List, Map } from 'immutable';
import * as utils from '../../utils'

const initialState = {
    byId: Map({}),
    byTest: Map({}),
    filteredIds: null
}
//Takes events and updates the state accordingly
export const reducer = (state = initialState, action) =>{
    
    switch(action.type){
        case actionTypes.LOADED: {
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    state.byId = state.byId.set(`${run.id}`, {
                        ...(state.byId.get(`${run.id}`) || {}), ...run
                    })
                })
            }
            break;
        }
        case actionTypes.TESTID: {
            let testMap = state.byTest.get(action.id,Map({}));
            if ( !utils.isEmpty(action.runs) ) {
                action.runs.forEach(run => {
                    testMap = testMap.set(`${run.id}`, {
                        ...testMap.get(`${run.id}`, {}),
                        ...run
                    })
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
        default:
    }
    return state;
}
