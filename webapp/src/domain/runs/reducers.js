import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
const initialState = {
    byId: Map({}),
    byTest: Map({
        
    })
}
//Takes events and updates the state accordingly
export const reducer = (state = initialState, action) =>{

    switch(action.type){
        case actionTypes.LOADED: {
            action.runs.forEach(run=>{
                state.byId = state.byId.set(`${run.id}`,{
                    ...(state.byId.get(`${run.id}`)||{}),...run
                })
            })
            break;
        }
        case actionTypes.TESTID: {
            let testMap = state.byTest.get(action.id,Map({}));
            action.runs.forEach(run=>{
                testMap = testMap.set(`${run.id}`,{
                    ...testMap.get(`${run.id}`,{}),
                    ...run
                })
            })
            state.byTest = state.byTest.set(`${action.id}`,testMap)
            break;
        }
        default:
    }
    return state;
}