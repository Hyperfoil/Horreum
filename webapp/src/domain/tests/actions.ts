import * as api from './api';
import * as actionTypes from './actionTypes';
import { accessName, Access } from '../../auth'
import {
    Test,
    View,
    LoadingAction,
    LoadedAction,
    UpdateTokenAction,
    UpdateAccessAction,
    DeleteAction,
    UpdateTestWatchAction,
    UpdateViewAction,
    UpdateHookAction,
} from './reducers';
import { Dispatch } from 'react';
import * as notifications from '../../usersettings'
import { Map } from 'immutable';
import { alertAction, AddAlertAction } from '../../alerts'
import {Hook} from "../hooks/reducers";

const loaded = (tests: Test | Test[]): LoadedAction =>({
    type: actionTypes.LOADED,
    tests: Array.isArray(tests) ? tests: [tests]
})

export const fetchSummary = () => (dispatch: Dispatch<LoadingAction | LoadedAction>) => {
    dispatch({ type: actionTypes.LOADING })
    api.summary().then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )
}

export const fetchTest = (id: number) => (dispatch: Dispatch<LoadedAction>) =>
    api.get(id).then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([]))
    )

export const sendTest = (test: Test) => (dispatch: Dispatch<LoadedAction>) =>
    api.send(test).then(
        response => {
            dispatch(loaded(response))
            return response
        }
    )

export const updateView = (testId: number, view: View) => (dispatch: Dispatch<UpdateViewAction>) => {
    return api.updateView(testId, view).then(
        response => {
            dispatch({
                type: actionTypes.UPDATE_VIEW,
                testId, view
            })
            return response
        }
    )
}

export const updateHooks = (testId: number, testWebHooks: Hook[]) => (dispatch: Dispatch<UpdateHookAction>) => {

    const promises: any[] = [] ;

    testWebHooks.forEach( hook => {
        promises.push( api.updateHook(testId, hook).then(
            response => {
                dispatch({
                    type: actionTypes.UPDATE_HOOK,
                    testId, hook
                })
                return response
            }
        )
    )
    })
    return Promise.all(promises);
}

export const resetToken = (id: number) => (dispatch: Dispatch<UpdateTokenAction>) =>
    api.resetToken(id).then(
        token => dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id: id,
            token: token,
        })
    )

export const dropToken = (id: number) => (dispatch: Dispatch<UpdateTokenAction>) =>
    api.dropToken(id).then(
        () => dispatch({
               type: actionTypes.UPDATE_TOKEN,
               id: id,
               token: null,
        })
    )

export const updateAccess = (id: number, owner: string, access: Access) => (dispatch: Dispatch<UpdateAccessAction>) =>
    api.updateAccess(id, owner, accessName(access)).then(
        () => dispatch({ type: actionTypes.UPDATE_ACCESS, id, owner, access })
    )

export const deleteTest = (id: number) => (dispatch: Dispatch<DeleteAction>) =>
    api.deleteTest(id).then(
        () => dispatch({ type: actionTypes.DELETE, id })
    )

export const fetchTestWatch = () => (dispatch: Dispatch<UpdateTestWatchAction>) =>
    notifications.fetchTestWatch().then(
        response => dispatch({
            type: actionTypes.UPDATE_TEST_WATCH,
            byId: Map(Object.entries(response).map(([key, value]) => [parseInt(key), value as string[]])) })
)

export const addTestWatch = (id: number, userOrTeam: string) => (dispatch: Dispatch<UpdateTestWatchAction | AddAlertAction>) => {
    dispatch({
        type: actionTypes.UPDATE_TEST_WATCH,
        byId: Map([[id, undefined]])
    })
    notifications.addTestWatch(id, userOrTeam).then(
        response => dispatch({
            type: actionTypes.UPDATE_TEST_WATCH,
            byId: Map([[id, response as string[]]])
        }),
        error => dispatch(alertAction("ADD_TEST_WATCH", "Failed to add test watch", error))
    )
}

export const removeTestWatch = (id: number, userOrTeam: string) => (dispatch: Dispatch<UpdateTestWatchAction | AddAlertAction>) => {
    dispatch({
        type: actionTypes.UPDATE_TEST_WATCH,
        byId: Map([[id, undefined]])
    })
    notifications.removeTestWatch(id, userOrTeam).then(
        response => dispatch({
            type: actionTypes.UPDATE_TEST_WATCH,
            byId: Map([[id, response as string[]]])
        }),
        error => dispatch(alertAction("REMOVE_TEST_WATCH", "Failed to add test watch", error))
    )
}