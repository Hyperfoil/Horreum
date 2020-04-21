import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";

const initialState = {
    byId: undefined
}

export const reducer = (state = initialState, action) => {
    switch (action.type) {
        case actionTypes.LOADED:
            if (!state.byId) {
                state.byId = Map({})
            }
            if (!utils.isEmpty(action.tests)) {
                action.tests.forEach(test => {
                    if (test.id !== null && typeof test.id !== "undefined") {
                        state.byId = state.byId.set("t" + test.id, {
                            ...(state.byId.get("t" + test.id, {})), ...test
                        })
                    }
                })
            }
        break;
        case actionTypes.UPDATE_TOKEN: {
            let test = state.byId && state.byId.get("t" + action.id)
            if (test) {
               state.byId = state.byId.set("t" + action.id, { ...test, token: action.token })
            }
        }
        break;
        case actionTypes.UPDATE_ACCESS: {
            let test = state.byId && state.byId.get("t" + action.id)
            if (test) {
               state.byId = state.byId.set("t" + action.id, { ...test, owner: action.owner, access: action.access })
            }
        }
        break;
        default:
    }
    return state;
}
