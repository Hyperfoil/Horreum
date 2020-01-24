import {createBrowserHistory} from 'history'
import { createStore, combineReducers, compose, applyMiddleware } from 'redux';
import {connectRouter} from 'connected-react-router'
import thunk from 'redux-thunk';
//import { persistStore, autoRehydrate } from 'redux-persist';

import { reducer as runReducer} from './domain/runs/reducers'
import { reducer as testReducer} from './domain/tests/reducers'
import { reducer as hookReducer} from './domain/hooks/reducers'
import { reducer as schemaReducer} from './domain/schemas/reducers'
import { reducer as authReducer} from './auth.js'

export const history = createBrowserHistory();

const appReducers = combineReducers({
    router: connectRouter(history),
    runs:  runReducer,
    tests: testReducer,
    hooks: hookReducer,
    schemas: schemaReducer,
    auth: authReducer,
})
const enhancer = compose(
    applyMiddleware(
        thunk,
    ),
)
const store = createStore(
    appReducers,
    enhancer,
)

export default store;


