import { Dispatch } from "react"
import { Access } from "../../auth"
import { Team } from "../../components/TeamSelect"
import {
    LoadedAction,
    LoadingAction,
    LoadSuggestionsAction,
    Run,
    SelectRolesAction,
    SuggestAction,
    TestIdAction,
    UpdateAccessAction,
    UpdateTokenAction,
    TrashAction,
    UpdateDescriptionAction,
    UpdateSchemaAction,
} from "../runs/reducers"
import * as actionTypes from "./actionTypes"
import * as api from "./api"
import { isFetchingSuggestions, suggestQuery } from "./selectors"
import store from "../../store"
import { PaginationInfo } from "../../utils"

const loaded = (run: Run | Run[], total?: number): LoadedAction => ({
    type: actionTypes.LOADED,
    runs: Array.isArray(run) ? run : [run],
    total,
})

const testId = (id: number, runs: Run[], total: number, tags: string): TestIdAction => ({
    type: actionTypes.TESTID,
    id,
    runs,
    total,
})

export const get = (id: number, token?: string) => (dispatch: Dispatch<LoadedAction>) =>
    api.get(id, token).then(
        response => dispatch(loaded(response)),
        error => dispatch(loaded([], 0))
    )

export const byTest =
    (id: number, pagination: PaginationInfo, trashed: boolean, tags: string) =>
    (dispatch: Dispatch<LoadingAction | TestIdAction>) => {
        dispatch({ type: actionTypes.LOADING })
        api.byTest(id, pagination, trashed, tags).then(
            response => dispatch(testId(id, response.runs, response.total, tags)),
            error => dispatch(testId(id, [], 0, ""))
        )
    }

export const list =
    (query: string, matchAll: boolean, roles: string, pagination: PaginationInfo, trashed: boolean) =>
    (dispatch: Dispatch<LoadedAction>) => {
        return api.list(query, matchAll, roles, pagination, trashed).then(response => {
            dispatch(loaded(response.runs, response.total))
        })
    }

export const suggest =
    (query: string, roles: string) => (dispatch: Dispatch<SuggestAction | LoadSuggestionsAction>) => {
        if (query === "") {
            dispatch({
                type: actionTypes.SUGGEST,
                responseReceived: false,
                options: [],
            })
        } else {
            let fetching = isFetchingSuggestions(store.getState())
            dispatch({
                type: actionTypes.LOAD_SUGGESTIONS,
                query: query,
            })
            if (!fetching) {
                fetchSuggestions(query, roles, dispatch)
            }
        }
    }

const fetchSuggestions = (query: string, roles: string, dispatch: Dispatch<SuggestAction | LoadSuggestionsAction>) => {
    api.suggest(query, roles)
        .then(
            response => {
                dispatch({
                    type: actionTypes.SUGGEST,
                    responseReceived: true,
                    options: response,
                })
            },
            e => {
                dispatch({
                    type: actionTypes.SUGGEST,
                    responseReceived: true,
                    options: [],
                })
            }
        )
        .finally(() => {
            let nextQuery = suggestQuery(store.getState())
            if (nextQuery != null) {
                fetchSuggestions(nextQuery, roles, dispatch)
            }
        })
}

export const selectRoles = (selection: Team): SelectRolesAction => {
    return {
        type: actionTypes.SELECT_ROLES,
        selection: selection,
    }
}

export const resetToken = (id: number, testid: number) => (dispatch: Dispatch<UpdateTokenAction>) => {
    return api.resetToken(id).then(token => {
        dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id,
            testid,
            token,
        })
    })
}

export const dropToken = (id: number, testid: number) => (dispatch: Dispatch<UpdateTokenAction>) => {
    return api.dropToken(id).then(response => {
        dispatch({
            type: actionTypes.UPDATE_TOKEN,
            id,
            testid,
            token: null,
        })
    })
}

export const updateAccess =
    (id: number, testid: number, owner: string, access: Access) => (dispatch: Dispatch<UpdateAccessAction>) => {
        return api.updateAccess(id, owner, access).then(response => {
            dispatch({
                type: actionTypes.UPDATE_ACCESS,
                id,
                testid,
                owner,
                access,
            })
        })
    }

export const trash =
    (id: number, testid: number, isTrashed: boolean = true) =>
    (dispatch: Dispatch<TrashAction>) =>
        api.trash(id, isTrashed).then(response => {
            dispatch({
                type: actionTypes.TRASH,
                id,
                testid,
                isTrashed,
            })
        })

export const updateDescription =
    (id: number, testid: number, description: string) => (dispatch: Dispatch<UpdateDescriptionAction>) =>
        api.updateDescription(id, description).then(response => {
            dispatch({
                type: actionTypes.UPDATE_DESCRIPTION,
                id,
                testid,
                description,
            })
        })

export const updateSchema =
    (id: number, testid: number, path: string | undefined, schemaid: number, schema: string) =>
    (dispatch: Dispatch<UpdateSchemaAction>) =>
        api.updateSchema(id, path, schema).then(schemas =>
            dispatch({
                type: actionTypes.UPDATE_SCHEMA,
                id,
                testid,
                path,
                schema,
                schemas,
            })
        )
