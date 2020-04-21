import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";
const initialState = {
    byId: undefined,
}
export const reducer = (state = initialState, action) =>{
    switch(action.type){
        case actionTypes.LOADED: {
            if ( !utils.isEmpty(action.hooks) ) {
                action.hooks.forEach(hook => {
                    const byId = state.byId || Map({})
                    state.byId = byId.set(`${hook.id}`, {
                        ...(byId.get(`${hook.id}`) || {}), ...hook
                    })
                })
            }
         
        }
        break;
        case actionTypes.DELETE: {
            const byId = state.byId || Map({})
            if ( byId.has(`${action.id}`) ){
                state.byId = byId.delete(`${action.id}`)
            }
        }
        break;
        default:
    }
    return state;
}
