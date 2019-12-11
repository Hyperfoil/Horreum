import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
const initialState = {
    byId: Map({}),
}
export const reducer = (state = initialState, action) =>{

    switch(action.type){
        case actionTypes.LOADED: {
            action.hooks.forEach(hook=>{
                state.byId = state.byId.set(`${hook.id}`,{
                    ...(state.byId.get(`${hook.id}`)||{}),...hook
                })
            })
            break;
        }
        default:
    }
    return state;
}