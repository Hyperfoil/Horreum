import { combineReducers, configureStore } from '@reduxjs/toolkit'

import { AuthState, reducer as authReducer } from "./auth"

export interface State {
    auth: AuthState
}

const store = configureStore({
 reducer: combineReducers({
  auth: authReducer,
 }),
})

export default store
