import { Dispatch } from "redux"
import { Access } from "../../auth"
import { Team } from "../../components/TeamSelect"
import {
    LoadedAction,
    LoadingAction,
    LoadSuggestionsAction,
    SelectRolesAction,
    SuggestAction,
    TestIdAction,
    UpdateAccessAction,
    UpdateTokenAction,
    TrashAction,
    UpdateDescriptionAction,
    UpdateSchemaAction,
    UpdateDatasetsAction,
} from "../runs/reducers"
import * as actionTypes from "./actionTypes"
import Api, {RunExtended, RunSummary, SortDirection} from "../../api"
import { isFetchingSuggestions, suggestQuery } from "./selectors"
import store from "../../store"
import { PaginationInfo } from "../../utils"
import { AddAlertAction, alertAction, dispatchError } from "../../alerts"

const loaded = (run: RunExtended | undefined, total?: number): LoadedAction => ({
    type: actionTypes.LOADED,
    run,
    total,
})

const testId = (id: number, runs: RunSummary[], total: number): TestIdAction => ({
    type: actionTypes.TESTID,
    id,
    runs,
    total,
})

export function get(id: number, token?: string) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.runServiceGetRun(id, token).then(
            response => dispatch(loaded(response)),
            error => {
                dispatch(alertAction("FETCH_RUN", "Failed to fetch data for run " + id, error))
                dispatch(loaded(undefined, 0))
                return Promise.reject(error)
            }
        )
}

export function getSummary(id: number, token?: string) {
    return (dispatch: Dispatch<LoadedAction | AddAlertAction>) =>
        Api.runServiceGetRunSummary(id, token).then(
            response =>
                dispatch(
                    loaded({
                        data: JSON,
                        schemas: [],
                        metadata: response.hasMetadata ? {} : undefined,
                        ...response,
                    })
                ),
            error => {
                dispatch(alertAction("FETCH_RUN_SUMMARY", "Failed to fetch data for run " + id, error))
                dispatch(loaded(undefined, 0))
                return Promise.reject(error)
            }
        )
}

export function byTest(id: number, pagination: PaginationInfo, trashed: boolean) {
    return (dispatch: Dispatch<LoadingAction | TestIdAction | AddAlertAction>) => {
        dispatch({ type: actionTypes.LOADING })
        return Api.runServiceListTestRuns(
            id,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
            pagination.perPage,
            pagination.page,
            pagination.sort,
            trashed
        ).then(
            response => dispatch(testId(id, response.runs, response.total)),
            error => {
                dispatch(alertAction("FETCH_RUNS", "Failed to fetch runs for test " + id, error))
                dispatch(testId(id, [], 0))
                return Promise.reject(error)
            }
        )
    }
}

export function suggest(query: string, roles: string) {
    return (dispatch: Dispatch<SuggestAction | LoadSuggestionsAction | AddAlertAction>) => {
        if (query === "") {
            dispatch({
                type: actionTypes.SUGGEST,
                responseReceived: false,
                options: [],
            })
        } else {
            const fetching = isFetchingSuggestions(store.getState())
            dispatch({
                type: actionTypes.LOAD_SUGGESTIONS,
                query: query,
            })
            if (!fetching) {
                fetchSuggestions(query, roles, dispatch)
            }
        }
    }
}

function fetchSuggestions(
    query: string,
    roles: string,
    dispatch: Dispatch<SuggestAction | LoadSuggestionsAction | AddAlertAction>
) {
    Api.runServiceAutocomplete(query)
        .then(
            response => {
                dispatch({
                    type: actionTypes.SUGGEST,
                    responseReceived: true,
                    options: response,
                })
            },
            error => {
                dispatch(alertAction("FETCH_SUGGESTIONS", "Failed to fetch suggestions", error))
                dispatch({
                    type: actionTypes.SUGGEST,
                    responseReceived: true,
                    options: [],
                })
            }
        )
        .finally(() => {
            const nextQuery = suggestQuery(store.getState())
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

export function resetToken(id: number, testid: number) {
    return (dispatch: Dispatch<UpdateTokenAction | AddAlertAction>) => {
        return Api.runServiceResetToken(id).then(
            token => {
                dispatch({
                    type: actionTypes.UPDATE_TOKEN,
                    id,
                    testid,
                    token,
                })
            },
            error => dispatchError(dispatch, error, "RESET_RUN_TOKEN", "Failed to reset token for run " + id)
        )
    }
}

export const dropToken = (id: number, testid: number) => (dispatch: Dispatch<UpdateTokenAction | AddAlertAction>) => {
    return Api.runServiceDropToken(id).then(
        _ => {
            dispatch({
                type: actionTypes.UPDATE_TOKEN,
                id,
                testid,
                token: null,
            })
        },
        error => dispatchError(dispatch, error, "DROP_RUN_TOKEN", "Failed to drop run token")
    )
}

export function updateAccess(id: number, testid: number, owner: string, access: Access) {
    return (dispatch: Dispatch<UpdateAccessAction | AddAlertAction>) => {
        return Api.runServiceUpdateAccess(id, access, owner).then(
            _ => {
                dispatch({
                    type: actionTypes.UPDATE_ACCESS,
                    id,
                    testid,
                    owner,
                    access,
                })
            },
            error => dispatchError(dispatch, error, "UPDATE_RUN_ACCESS", "Failed to update run access")
        )
    }
}

export function trash(id: number, testid: number, isTrashed = true) {
    return (dispatch: Dispatch<TrashAction | AddAlertAction>) =>
        Api.runServiceTrash(id, isTrashed).then(
            _ => {
                dispatch({
                    type: actionTypes.TRASH,
                    id,
                    testid,
                    isTrashed,
                })
            },
            error => dispatchError(dispatch, error, "RUN_TRASH", "Failed to restore run ID " + id)
        )
}

export function updateDescription(id: number, testid: number, description: string) {
    return (dispatch: Dispatch<UpdateDescriptionAction | AddAlertAction>) =>
        Api.runServiceUpdateDescription(id, description).then(
            _ => {
                dispatch({
                    type: actionTypes.UPDATE_DESCRIPTION,
                    id,
                    testid,
                    description,
                })
            },
            error => dispatchError(dispatch, error, "RUN_UPDATE", "Failed to update description for run ID " + id)
        )
}

export function updateSchema(id: number, testid: number, path: string | undefined, schemaUri: string) {
    return (dispatch: Dispatch<UpdateSchemaAction | AddAlertAction>) =>
        Api.runServiceUpdateSchema(id, schemaUri, path).then(
            schemas =>
                dispatch({
                    type: actionTypes.UPDATE_SCHEMA,
                    id,
                    testid,
                    path,
                    schema: schemaUri,
                    schemas,
                }),
            error => dispatchError(dispatch, error, "SCHEME_UPDATE_FAILED", "Failed to update run schema")
        )
}

export function recalculateDatasets(id: number, testid: number) {
    return (dispatch: Dispatch<UpdateDatasetsAction | AddAlertAction>) =>
        Api.runServiceRecalculateDatasets(id).then(
            datasets =>
                dispatch({
                    type: actionTypes.UPDATE_DATASETS,
                    id,
                    testid,
                    datasets,
                }),
            error => dispatchError(dispatch, error, "RECALCULATE_DATASETS", "Failed to recalculate datasets")
        )
}
