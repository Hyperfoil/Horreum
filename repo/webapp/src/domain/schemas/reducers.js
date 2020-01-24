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
                state.byId = state.byId.clear()
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
        case actionTypes.UPDATE_TOKEN: {
            let schema = state.byId.get(`${action.id}`)
            if (schema) {
                state.byId = state.byId.set(`${action.id}`, { ...schema, token: action.token })
            }
        }
        break;
        case actionTypes.UPDATE_ACCESS: {
            let schema = state.byId.get(`${action.id}`)
            if (schema) {
                state.byId = state.byId.set(`${action.id}`, { ...schema, owner: action.owner, access: action.access })
            }
        }
        break;
        default:
    }
    return state;
}
