import { fetchApi } from "../../services/api"
import { Change, Variable } from "./types"
import { fingerprintToString } from "./grafanaapi"

const base = "/api/alerting"
const endPoints = {
    base: () => `${base}`,
    variables: (testId: number) => `${base}/variables?test=${testId}`,
    dashboard: (testId: number, fingerprint?: string) =>
        `${base}/dashboard?test=${testId}&fingerprint=${fingerprint ? encodeURIComponent(fingerprint) : ""}`,
    changes: (varId: number) => `${base}/changes?var=${varId}`,
    change: (changeId: number) => `${base}/change/${changeId}`,
    recalculate: (testId: number, debug: boolean, from?: number, to?: number) =>
        `${base}/recalculate?test=${testId}&debug=${debug}${from ? "&from=" + from : ""}${to ? "&to=" + to : ""}`,
    lastDatapoints: () => `${base}/datapoint/last`,
    models: () => `${base}/models`,
    defaultChangeDetectionConfigs: () => `${base}/defaultChangeDetectionConfigs`,
}

export const fetchVariables = (testId: number) => {
    return fetchApi(endPoints.variables(testId), null, "get")
}

export const updateVariables = (testId: number, variables: Variable[]) => {
    return fetchApi(endPoints.variables(testId), variables, "post", {}, "response")
}

export const fetchDashboard = (testId: number, fingerprint?: string) => {
    return fetchApi(endPoints.dashboard(testId, fingerprint), null, "get")
}

export const fetchChanges = (varId: number) => {
    return fetchApi(endPoints.changes(varId), null, "get")
}

export const updateChange = (change: Change) => {
    return fetchApi(endPoints.change(change.id), change, "post", {}, "response")
}

export const deleteChange = (changeId: number) => {
    return fetchApi(endPoints.change(changeId), null, "delete", {}, "response")
}

export const recalculate = (testId: number, debug: boolean, fromTimestamp?: number, toTimestamp?: number) => {
    return fetchApi(endPoints.recalculate(testId, debug, fromTimestamp, toTimestamp), null, "post", {}, "response")
}

export const recalculateProgress = (testId: number) => {
    return fetchApi(endPoints.recalculate(testId, false), null, "get")
}

export const findLastDatapoints = (variableIds: number[], fingerprint: unknown) => {
    return fetchApi(
        endPoints.lastDatapoints(),
        { variables: variableIds, fingerprint: fingerprintToString(fingerprint) },
        "post"
    )
}

export function models() {
    return fetchApi(endPoints.models(), null, "get")
}

export function defaultChangeDetectionConfigs() {
    return fetchApi(endPoints.defaultChangeDetectionConfigs(), null, "get")
}
