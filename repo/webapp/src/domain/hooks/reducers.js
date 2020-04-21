import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";
const initialState = {
    byId: undefined,
}
export const reducer = (state = initialState, action) =>{
    switch(action.type){
        case actionTypes.LOADED: {
            if (!state.byId) {
                state.byId = Map({})
            }
            if ( !utils.isEmpty(action.hooks) ) {
                action.hooks.forEach(hook => {
                    state.byId = state.byId.set(`${hook.id}`, {
                        ...(state.byId.get(`${hook.id}`) || {}), ...hook
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
