import { fetchApi } from "../../services/api"
import { Change, Variable } from "./types"
import { fingerprintToString } from "./grafanaapi"
import { MissingDataRule } from "./types"

const base = "/api/alerting"
const endPoints = {
    base: () => `${base}`,
    variables: (testId: number) => `${base}/variables?test=${testId}`,
    dashboard: (testId: number, fingerprint: string) => `${base}/dashboard?test=${testId}&fingerprint=${fingerprint}`,
    changes: (varId: number, fingerprint: string) => `${base}/changes?var=${varId}&fingerprint=${fingerprint}`,
    change: (changeId: number) => `${base}/change/${changeId}`,
    recalculate: (testId: number, debug: boolean, from?: number, to?: number) =>
        `${base}/recalculate?test=${testId}&debug=${debug}${from ? "&from=" + from : ""}${to ? "&to=" + to : ""}`,
    lastDatapoints: () => `${base}/datapoint/last`,
    models: () => `${base}/models`,
    defaultChangeDetectionConfigs: () => `${base}/defaultChangeDetectionConfigs`,
    missingDataRule: (testId: number, ruleId?: number) =>
        `${base}/missingdatarule${ruleId !== undefined ? "/" + ruleId : ""}?testId=${testId}`,
}

export const fetchVariables = (testId: number) => {
    return fetchApi(endPoints.variables(testId), null, "get")
}

export const updateVariables = (testId: number, variables: Variable[]) => {
    return fetchApi(endPoints.variables(testId), variables, "post", {}, "response")
}

export const fetchDashboard = (testId: number, fingerprint: unknown) => {
    return fetchApi(endPoints.dashboard(testId, fingerprintToString(fingerprint)), null, "get")
}

export const fetchChanges = (varId: number, fingerprint: unknown) => {
    return fetchApi(endPoints.changes(varId, fingerprintToString(fingerprint)), null, "get")
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

export function fetchMissingDataRules(testId: number): Promise<MissingDataRule[]> {
    return fetchApi(endPoints.missingDataRule(testId), null, "get")
}

export function updateMissingDataRule(rule: MissingDataRule) {
    return fetchApi(endPoints.missingDataRule(rule.testId), rule, "post")
}

export function deleteMissingDataRule(rule: MissingDataRule) {
    return fetchApi(endPoints.missingDataRule(rule.testId, rule.id), null, "delete")
}
