import { createStore, combineReducers, compose, applyMiddleware, StoreEnhancer } from "redux"
import thunk from "redux-thunk"

import { AuthState, reducer as authReducer } from "./auth"


export interface State {
    auth: AuthState
}

const appReducers = combineReducers({
    auth: authReducer,
})
const enhancer = compose(applyMiddleware(thunk),  enableDevMode())
const store = createStore(appReducers, enhancer)
export function enableDevMode(): StoreEnhancer {
    if (!process.env.NODE_ENV || process.env.NODE_ENV === "development") {
        return (
            ((window as any).__REDUX_DEVTOOLS_EXTENSION__ && (window as any).__REDUX_DEVTOOLS_EXTENSION__()) ||
            (creator => creator)
        )
    } else {
        return creator => creator
    }
}

export default store
