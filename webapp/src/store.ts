import { createBrowserHistory } from "history"
import {legacy_createStore as createStore, combineReducers, compose, applyMiddleware, StoreEnhancer } from "redux"
import { createRouterReducer } from "@lagunovsky/redux-react-router"
import thunk from "redux-thunk"

import { RunsState, reducer as runReducer } from "./domain/runs/reducers"
import { TestsState, reducer as testReducer } from "./domain/tests/reducers"
import { ActionsState, reducer as actionReducer } from "./domain/actions/reducers"
import { SchemasState, reducer as schemaReducer } from "./domain/schemas/reducers"
import { AuthState, reducer as authReducer } from "./auth"
import { Alert, reducer as alertReducer } from "./alerts"

export const history = createBrowserHistory()

export interface State {
    auth: AuthState
    alerts: Alert[]
    actions: ActionsState
    runs: RunsState
    schemas: SchemasState
    tests: TestsState
}

const appReducers = combineReducers({
    router: createRouterReducer(history),
    runs: runReducer,
    tests: testReducer,
    actions: actionReducer,
    schemas: schemaReducer,
    auth: authReducer,
    alerts: alertReducer,
})
const enhancer = compose(applyMiddleware(thunk), enableDevMode())
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
