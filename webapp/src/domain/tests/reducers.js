import * as actionTypes from './actionTypes';
import { Map } from 'immutable';
const initialState = {
    byId: Map({})
}

export const reducer = (state = initialState, action) => {
    switch (action.type) {
        case actionTypes.LOADED: {
            action.tests.forEach(test => {
                if (test.id !== null && typeof test.id !== "undefined") {
                    state.byId = state.byId.set("t"+test.id, {
                        ...(state.byId.get("t"+test.id,{})), ...test
                    })
                }
            })

            break;
        }
    }
    return state;
}