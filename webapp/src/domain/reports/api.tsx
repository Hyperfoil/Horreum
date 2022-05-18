import { fetchApi } from "../../services/api"
import { PaginationInfo, paginationParams } from "../../utils"
import { Test } from "../tests/reducers"

export type TableReportConfig = {
    id: number
    title: string
    test?: Partial<Test>

    filterLabels: string[] | null
    filterFunction?: string

    categoryLabels: string[] | null
    categoryFunction?: string
    categoryFormatter?: string

    seriesLabels: string[] | null
    seriesFunction?: string
    seriesFormatter?: string

    scaleLabels: string[] | null
    scaleFunction?: string
    scaleFormatter?: string
    scaleDescription?: string

    components: ReportComponent[]
}

export type ReportComponent = {
    id: number
    name: string
    order: number
    labels: string[]
    function?: string
    unit?: string
}

export type ReportComment = {
    id: number
    level: number
    category?: string
    componentId?: number
    comment: string
}

export type TableReport = {
    id: number
    config: TableReportConfig
    created: number
    data: TableReportData[]
    comments: ReportComment[]
}

export type TableReportData = {
    datasetId: number
    runId: number
    ordinal: number
    category: string
    series: string
    scale: string
    values: any[]
}

export type AllTableReports = {
    count: number
    reports: TableReportSummary[]
}

export type TableReportSummary = {
    testId: number
    testName: string
    title: string
    reports: { id: number; configId: number; created: number }[]
}

const base = "/api/report"
const endPoints = {
    tableReports: (pagination: PaginationInfo, testid?: number, roles?: string, folder?: string) =>
        `${base}/table?${testid !== undefined ? `test=${testid}&` : ""}${
            roles ? `owner=${encodeURIComponent(roles)}&` : ""
        }${paginationParams(pagination)}&folder=${folder === undefined ? "*" : folder}`,
    tableReportConfig: (configId?: number, reportId?: number) =>
        `${base}/table/config${configId ? "/" + configId : ""}${reportId ? "?edit=" + reportId : ""}`,
    tableReportPreview: (reportId?: number) => `${base}/table/preview${reportId ? "?edit=" + reportId : ""}`,
    tableReport: (id: number) => `${base}/table/${id}`,
    comment: (reportId: number) => `${base}/comment/${reportId}`,
}

export function getTableReports(pagination: PaginationInfo, testid?: number, roles?: string, folder?: string) {
    return fetchApi(endPoints.tableReports(pagination, testid, roles, folder), null, "get")
}

export function getTableConfig(configId: number) {
    return fetchApi(endPoints.tableReportConfig(configId), null, "get")
}

export function updateTableConfig(config: TableReportConfig, reportId?: number) {
    return fetchApi(endPoints.tableReportConfig(undefined, reportId), config, "post")
}

export function previewTableReport(config: TableReportConfig, reportId?: number) {
    return fetchApi(endPoints.tableReportPreview(reportId), config, "post")
}

export function getTableReport(id: number) {
    return fetchApi(endPoints.tableReport(id), null, "get")
}

export function deleteTableReport(id: number) {
    return fetchApi(endPoints.tableReport(id), null, "delete")
}

export function updateComment(reportId: number, comment: ReportComment) {
    return fetchApi(endPoints.comment(reportId), comment, "post")
}
