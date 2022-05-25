import { fetchApi } from "../../services/api"
import { Schema } from "./reducers"
import { Access, accessName } from "../../auth"
const base = "/api/schema"
const endPoints = {
    base: () => `${base}`,
    descriptors: () => `${base}/descriptors`,
    crud: (id: number) => `${base}/${id}/`,
    byUri: (uri: string) => `${base}/idByUri/${encodeURIComponent(uri)}`,
    resetToken: (id: number) => `${base}/${id}/resetToken`,
    dropToken: (id: number) => `${base}/${id}/dropToken`,
    updateAccess: (id: number, owner: string, access: Access) =>
        `${base}/${id}/updateAccess?owner=${owner}&access=${accessName(access)}`,
    findUsages: (label: string) => `${base}/findUsages?label=${encodeURIComponent(label)}`,
    testJsonPath: (jsonpath: string) => `/api/sql/testjsonpath?query=${encodeURIComponent(jsonpath)}`,
    transformers: (schemaId: number, transformerId?: number) =>
        `${base}/${schemaId}/transformers${transformerId !== undefined ? "/" + transformerId : ""}`,
    allTransformers: () => `${base}/allTransformers`,
    labels: (schemaId: number, labelId?: number) =>
        `${base}/${schemaId}/labels${labelId !== undefined ? "/" + labelId : ""}`,
    allLabels: (name?: string) => `${base}/allLabels${name ? "?name=" + encodeURIComponent(name) : ""}`,
}
export const all = () => {
    return fetchApi(endPoints.base(), null, "get")
}
export const descriptors = () => {
    return fetchApi(endPoints.descriptors(), null, "get")
}

export const add = (payload: Schema) => {
    return fetchApi(endPoints.base(), payload, "post")
}
export const getById = (id: number) => {
    return fetchApi(endPoints.crud(id), null, "get")
}

export function getIdByUri(uri: string): Promise<number> {
    return fetchApi(endPoints.byUri(uri), null, "get")
}

export const resetToken = (id: number) => fetchApi(endPoints.resetToken(id), null, "post", {}, "text")

export const dropToken = (id: number) => fetchApi(endPoints.dropToken(id), null, "post")

export const updateAccess = (id: number, owner: string, access: Access) => {
    // TODO: fetchival does not support form parameters, it tries to JSONify everything
    return fetchApi(endPoints.updateAccess(id, owner, access), null, "post", {}, "response")
    //                   "owner=" + encodeURIComponent(owner) + "&access=" + encodeURIComponent(access),
    //                   'post', { 'content-type' : 'application/x-www-form-urlencoded'}, 'response')
}

export const deleteSchema = (id: number) => fetchApi(endPoints.crud(id), null, "delete")

export const testJsonPath = (jsonpath: string) => fetchApi(endPoints.testJsonPath(jsonpath), null, "get")

export function findUsages(label: string) {
    return fetchApi(endPoints.findUsages(label), null, "get")
}

export function listTransformers(schemaId: number): Promise<Transformer[]> {
    return fetchApi(endPoints.transformers(schemaId), null, "get")
}

export function addOrUpdateTransformer(transformer: Transformer) {
    return fetchApi(endPoints.transformers(transformer.schemaId), transformer, "post")
}

export function deleteTransformer(transformer: Transformer) {
    return fetchApi(endPoints.transformers(transformer.schemaId, transformer.id), null, "delete")
}

export function allTransformers(): Promise<TransformerInfo[]> {
    return fetchApi(endPoints.allTransformers(), null, "get")
}

export function listLabels(schemaId: number): Promise<Label[]> {
    return fetchApi(endPoints.labels(schemaId), null, "get")
}

export function addOrUpdateLabel(label: Label) {
    return fetchApi(endPoints.labels(label.schemaId), label, "post")
}

export function deleteLabel(label: Label) {
    return fetchApi(endPoints.labels(label.schemaId, label.id), null, "delete")
}

export function listAllLabels(name?: string): Promise<LabelInfo[]> {
    return fetchApi(endPoints.allLabels(name), null, "get")
}

export type ValidationResult = {
    valid: boolean
    reason: string
    errorCode: number
    sqlState: string
}

export interface Label {
    id: number
    name: string
    extractors: Extractor[]
    function?: string
    owner: string
    access: Access
    schemaId: number
    // temporary fields
    modified?: boolean
}

export interface SchemaDescriptor {
    name: string
    uri: string
    id: number
}

export interface LabelInfo {
    name: string
    metrics: boolean
    filtering: boolean
    schemas: SchemaDescriptor[]
}

export interface LabelLocation {
    type: "VARIABLE" | "VIEW" | "REPORT" | "FINGERPRINT" | "MISSINGDATA_RULE"
    testId: number
    testName: string
}

export interface LabelInVariable extends LabelLocation {
    variableId: number
    variableName: string
}

export interface LabelInView extends LabelLocation {
    viewId: number
    viewName: string
    componentId: number
    header: string
}

export interface LabelInReport extends LabelLocation {
    configId: number
    title: string
    where: "component" | "filter" | "category" | "series" | "label"
    name: string | null
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface LabelInFingerprint extends LabelLocation {}

export interface LabelInRule extends LabelLocation {
    ruleId: number
    ruleName: string
}

export type Extractor = {
    name: string
    jsonpath: string
    array: boolean
    validationTimer?: any
    validationResult?: ValidationResult
}

export type Transformer = {
    id: number
    schemaId: number
    schemaUri: string
    schemaName: string
    name: string
    description: string
    targetSchemaUri?: string
    extractors: Extractor[]
    function?: string
    owner: string
    access: Access
    modified?: boolean
}

export type TransformerInfo = {
    schemaId: number
    schemaUri: string
    schemaName: string
    transformerId: number
    transformerName: string
}
