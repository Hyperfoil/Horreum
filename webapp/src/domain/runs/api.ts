import { fetchApi } from "../../services/api"
import { Access, accessName } from "../../auth"
import { PaginationInfo, paginationParams } from "../../utils"
import { Label, SchemaDescriptor } from "../schemas/api"

const runApi = {
    // getRun: (runId: number, token?: string) => `/api/run/${runId}${token ? "?token=" + token : ""}`,
    getData: (runId: number, token?: string) => `/api/run/${runId}/data${token ? "?token=" + token : ""}`,
    getSummary: (runId: number, token?: string) => `/api/run/${runId}/summary${token ? "?token=" + token : ""}`,
    query: (runId: number, query: string, array: boolean, schemaUri?: string) =>
        `/api/run/${runId}/query?query=${encodeURIComponent(query)}&array=${array}${
            schemaUri ? "&uri=" + schemaUri : ""
        }`,
    list: (query: string, matchAll: boolean, roles: string, pagination: PaginationInfo, trashed: boolean) =>
        `/api/run/list?${paginationParams(pagination)}&query=${encodeURIComponent(
            query
        )}&matchAll=${matchAll}&roles=${roles}&trashed=${trashed}`,
    suggest: (query: string, roles: string) =>
        `/api/run/autocomplete?query=${encodeURIComponent(query)}&roles=${roles}`,
    listByTest: (testId: number, pagination: PaginationInfo, trashed: boolean) =>
        `/api/run/list/${testId}?${paginationParams(pagination)}&trashed=${!!trashed}`,
    listBySchema: (uri: string, pagination: PaginationInfo) =>
        `/api/run/bySchema?uri=${encodeURIComponent(uri)}&${paginationParams(pagination)}`,
    resetToken: (runId: number) => `/api/run/${runId}/resetToken`,
    dropToken: (runId: number) => `/api/run/${runId}/dropToken`,
    updateAccess: (runId: number, owner: string, access: Access) =>
        `/api/run/${runId}/updateAccess?owner=${owner}&access=${accessName(access)}`,
    trash: (runId: number, isTrashed: boolean) => `/api/run/${runId}/trash?isTrashed=${isTrashed}`,
    description: (runId: number) => `/api/run/${runId}/description`,
    count: (testId: number) => `/api/run/count?testId=${testId}`,
    schema: (runId: number, path?: string) => `/api/run/${runId}/schema${(path && "?path=" + path) || ""}`,
    recalculate: (runId: number) => `/api/run/${runId}/recalculate`,
}

const datasetApi = {
    dataset: (datasetId: number) => `/api/dataset/${datasetId}`,
    query: (datasetId: number, query: string, array: boolean, schemaUri?: string) =>
        `/api/dataset/${datasetId}/query?query=${encodeURIComponent(query)}&array=${array}${
            schemaUri ? "&uri=" + schemaUri : ""
        }`,
    listByTest: (testId: number, pagination: PaginationInfo) =>
        `/api/dataset/list/${testId}?${paginationParams(pagination)}`,
    listBySchema: (uri: string, pagination: PaginationInfo) =>
        `/api/dataset/bySchema?uri=${encodeURIComponent(uri)}&${paginationParams(pagination)}`,
    labelValues: (datasetId: number) => `/api/dataset/${datasetId}/labelValues`,
    previewLabel: (datasetId: number) => `/api/dataset/${datasetId}/previewLabel`,
}

export const get = (id: number, token?: string) => {
    return fetchApi(runApi.getSummary(id, token), null, "get")
}

export const getData = (id: number, token?: string) => {
    return fetchApi(runApi.getData(id, token), null, "get")
}

export type QueryResult = {
    value: string
    valid: boolean
    jsonpath: string
    errorCode: number
    sqlState: string
    reason: string
    sql: string
}

export function query(id: number, query: string, array: boolean, schemaUri?: string): Promise<QueryResult> {
    return fetchApi(runApi.query(id, query, array, schemaUri), null, "get")
}

export const byTest = (id: number, pagination: PaginationInfo, trashed: boolean) =>
    fetchApi(runApi.listByTest(id, pagination, trashed), null, "get")

export function list(query: string, matchAll: boolean, roles: string, pagination: PaginationInfo, trashed: boolean) {
    return fetchApi(runApi.list(query, matchAll, roles, pagination, trashed), null, "get")
}

export function listBySchema(uri: string, pagination: PaginationInfo) {
    return fetchApi(runApi.listBySchema(uri, pagination), null, "get")
}

export const suggest = (query: string, roles: string) => fetchApi(runApi.suggest(query, roles), null, "get")

export const resetToken = (id: number) => fetchApi(runApi.resetToken(id), null, "post", {}, "text")

export const dropToken = (id: number) => fetchApi(runApi.dropToken(id), null, "post")

export const updateAccess = (id: number, owner: string, access: Access) => {
    // TODO: fetchival does not support form parameters, it tries to JSONify everything
    return fetchApi(runApi.updateAccess(id, owner, access), null, "post", {}, "response")
    //                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
    //                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

export const trash = (id: number, isTrashed: boolean) => fetchApi(runApi.trash(id, isTrashed), null, "post", {}, "text")

export const updateDescription = (id: number, description: string) => {
    return fetchApi(runApi.description(id), description, "post", { "Content-Type": "text/plain" }, "response")
}

export type RunCount = {
    active: number
    trashed: number
    total: number
}

export const runCount = (testId: number): Promise<RunCount> => fetchApi(runApi.count(testId), null, "get")

export const updateSchema = (id: number, path: string | undefined, schema: string) => {
    return fetchApi(runApi.schema(id, path), schema, "post", { "Content-Type": "text/plain" })
}

export function recalculateDatasets(runId: number) {
    return fetchApi(runApi.recalculate(runId), null, "post")
}

export function getDataset(datasetId: number) {
    return fetchApi(datasetApi.dataset(datasetId), null, "get")
}

export function queryDataset(datasetId: number, query: string, array: boolean, schemaUri?: string) {
    return fetchApi(datasetApi.query(datasetId, query, array, schemaUri), null, "get")
}

export function datasetsBySchema(uri: string, pagination: PaginationInfo) {
    return fetchApi(datasetApi.listBySchema(uri, pagination), null, "get")
}

export interface Dataset {
    id: number
    ordinal: number
    runId: number
    testId: number
    testname: string
    description?: string
    start: number
    stop: number
    owner: string
    access: number
    schemas: string[]
    view: any
}

export interface DatasetList {
    total: number
    datasets: Dataset[]
}

export function listTestDatasets(testId: number, pagination: PaginationInfo): Promise<DatasetList> {
    return fetchApi(datasetApi.listByTest(testId, pagination), null, "get")
}

export interface LabelValue {
    id: number
    name: string
    schema: SchemaDescriptor
    value: any
}

export function fetchLabelValues(datasetId: number): Promise<LabelValue[]> {
    return fetchApi(datasetApi.labelValues(datasetId), null, "get")
}

type LabelPreview = {
    value: any
    output: string
}

export function previewLabel(datasetId: number, label: Label): Promise<LabelPreview> {
    return fetchApi(datasetApi.previewLabel(datasetId), label, "post")
}
