import { createBrowserHistory } from "history"
import { createStore, combineReducers, compose, applyMiddleware, StoreEnhancer } from "redux"
import { createReduxHistoryContext } from "redux-first-history"
import thunk from "redux-thunk"

const { routerMiddleware, routerReducer } = createReduxHistoryContext({
    history: createBrowserHistory(),
})

import { AuthState, reducer as authReducer } from "./auth"


export interface State {
    auth: AuthState
}

const appReducers = combineReducers({
    router: routerReducer,
    auth: authReducer,
})
const enhancer = compose(applyMiddleware(thunk), applyMiddleware(routerMiddleware), enableDevMode())
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
