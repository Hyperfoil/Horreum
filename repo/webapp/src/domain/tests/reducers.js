import * as actionTypes from './actionTypes';
import {Map} from 'immutable';
import * as utils from "../../utils";

const initialState = {
    byId: Map({})
}

export const reducer = (state = initialState, action) => {
    switch (action.type) {
        case actionTypes.LOADED: {
            console.log("TEST.LOADED",action)
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
        }
    }
    return state;
}
