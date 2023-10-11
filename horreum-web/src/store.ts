import { createBrowserHistory } from "history"
import { createStore, combineReducers, compose, applyMiddleware, StoreEnhancer } from "redux"
import { createReduxHistoryContext } from "redux-first-history"
import thunk from "redux-thunk"

const { routerMiddleware, routerReducer } = createReduxHistoryContext({
    history: createBrowserHistory(),
    //other options if needed
})

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
    router: routerReducer,
    runs: runReducer,
    tests: testReducer,
    actions: actionReducer,
    schemas: schemaReducer,
    auth: authReducer,
    alerts: alertReducer,
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
