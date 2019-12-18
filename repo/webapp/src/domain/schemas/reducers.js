import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";
const initialState = {
    byId: Map({}),
}
export const reducer = (state = initialState, action) =>{
    switch(action.type){
        case actionTypes.LOADED: {
            if ( !utils.isEmpty(action.schemas) ) {
                action.schemas.forEach(schema => {
                    state.byId = state.byId.set(`${schema.id}`, {
                        ...(state.byId.get(`${schema.id}`) || {}), ...schema
                    })
                })
            }
         
        }
        break;
        case actionTypes.DELETE: {
            if(  state.byId.has(`${action.id}`) ){
                state.byId = state.byId.delete(`${action.id}`)
            }
            
        }
        break;
        default:
    }
    return state;
}
