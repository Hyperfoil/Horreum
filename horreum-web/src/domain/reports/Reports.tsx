import {useContext, useEffect, useMemo, useState} from "react"
import { useSelector } from "react-redux"

import { NavLink } from "react-router-dom"
import {
    Button,
    Toolbar,
    ToolbarContent,
    ToolbarGroup,
    ToolbarItem,
} from "@patternfly/react-core"
import { ArrowRightIcon, FolderOpenIcon, EditIcon } from "@patternfly/react-icons"

import ImportButton from "../../components/ImportButton"
import { Team } from "../../components/TeamSelect"
import { SelectedTest } from "../../components/TestSelect"
import { formatDateTime } from "../../utils"

import {AllTableReports, reportApi, SortDirection, TableReportConfig, TableReportSummary} from "../../api"
import ButtonLink from "../../components/ButtonLink"
import { useTester, teamsSelector } from "../../auth"

import ListReportsModal from "./ListReportsModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import CustomTable from "../../components/CustomTable"
import { ColumnDef, ColumnSort, createColumnHelper } from "@tanstack/react-table"

const columnHelper = createColumnHelper<TableReportSummary>()

type ReportGroup = {
    testId: number
    title?: string
}

export default function Reports(props: ReportGroup) {
    document.title = "Reports | Horreum"

    const { alerting } = useContext(AppContext) as AppContextType;
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sortBy, setSortBy] = useState<ColumnSort>({id: 'title', desc: true})
    const pagination = useMemo(() => ({ page, perPage, sortBy }), [page, perPage, sortBy])
    const [roles, setRoles] = useState<Team>()
    const test = {id: props.testId} as SelectedTest

    const [tableReports, setTableReports] = useState<AllTableReports>()
    const [tableReportsReloadCounter, setTableReportsReloadCounter] = useState(0)
    const [loading, setLoading] = useState(false)

    const [tableReportGroup, setTableReportGroup] = useState<ReportGroup>()
    const teams = useSelector(teamsSelector)

    useEffect(() => {
        setLoading(true)
        reportApi.getTableReports(
            pagination.sortBy.desc ? SortDirection.Descending : SortDirection.Ascending,
            undefined,
            pagination.perPage,
            pagination.page,
            roles?.key,
            pagination.sortBy.id,
            (test && test.id) || undefined
        )
            .then(setTableReports)
            .catch(error => alerting.dispatchError(error, "FETCH_REPORTS", "Failed to fetch reports"))
            .finally(() => setLoading(false))
    }, [pagination, roles, teams, tableReportsReloadCounter])

    const columns: ColumnDef<TableReportSummary, any>[] = useMemo(
        () => [
            columnHelper.accessor( 'title', {
                header: "Title",
                sortingFn: "text",
                cell: ({ row }) => {
                    const title = row.original.title;
                    const configId = row.original.configId;
                    return configId === undefined ? (
                        <div>{title}</div>
                    ) : (
                        <NavLink to={`/test/${test.id}/reports/table/config/${configId}`}>
                            {title} <EditIcon />
                        </NavLink>
                    )
                }
            }),
            columnHelper.accessor((summary) => summary.reports[0]?.created , {
                header: 'Last Report',
                id: 'created',
                enableSorting: false,
                cell: ({ row }) => {
                    const reports = row.original.reports;
                    return reports && reports.length > 0 ? (
                        <NavLink to={`/test/${test.id}/reports/table/${reports[0].id}`}>
                             <ArrowRightIcon />&nbsp;{formatDateTime(reports[0].created)}
                        </NavLink>
                    ) : (
                        <div>"No report"</div>
                    )
                }
            }),
            columnHelper.accessor((row) => row.reports.length, {
                header: 'Total Reports',
                id: 'count',
                enableSorting: false,
                cell: ({ row }) => {
                    return row.original.reports.length === 0 ? (
                        <span style={{ paddingLeft: "16px" }}>0</span>
                    ) : (
                       <Button
                            icon={<FolderOpenIcon/>}
                            variant="link"
                            style={{paddingTop: 0, paddingBottom: 0}}
                            onClick={() =>
                                setTableReportGroup({testId: row.original.testId, title:row.original.title})
                            }
                        >
                            {row.original.reports.length}
                        </Button>
                    )
                }
            })
        ],
        []
    )

    const tableReportSummary =
        (tableReportGroup !== undefined &&
            tableReports?.reports.find(
                summary => summary.testId === tableReportGroup.testId && summary.title === tableReportGroup.title
            )) ||
        undefined
    return (
        <>
            <Toolbar>
                <ToolbarContent>
                    <ToolbarGroup variant="action-group">
                        <ToolbarItem>
                            <ButtonLink to={`/test/${test.id}/reports/table/config/__new`}>New report configuration</ButtonLink>
                        </ToolbarItem>
                        <ToolbarItem>
                            <ImportButton
                                label="Import configuration"
                                onLoad={config => {
                                    if (!config.id) {
                                        return null
                                    }
                                    return reportApi.getTableReportConfig(config.id as number).then(
                                        existing => (
                                            <>
                                                This configuration is going to override table report configuration{" "}
                                                {existing.title} for test {existing.test?.name || "<unknown test>"})
                                                ({existing.id})
                                                {config?.title !== existing.title &&
                                                    ` using new title ${config?.title}`}
                                                .
                                                <br />
                                                <br />
                                                Do you really want to proceed?
                                            </>
                                        ),
                                        _ => null /* errors because the config does not exist => OK */
                                    )
                                }}
                                onImport={config => reportApi.importTableReportConfig(config as TableReportConfig)}
                                onImported={() => setTableReportsReloadCounter(tableReportsReloadCounter + 1)}
                            />
                        </ToolbarItem>
                    </ToolbarGroup>
                </ToolbarContent>
            </Toolbar>
            <CustomTable<TableReportSummary>
                columns={columns}
                data={tableReports?.reports || []}
                sortBy={[sortBy]}
                onSortBy={order => {
                    if (order.length > 0 && order[0]) {
                        setSortBy(order[0])
                    }
                }}
                isLoading={loading}
                pagination={{
                    top: true,
                    bottom: true,
                    count: tableReports?.count || 0,
                    perPage: perPage,
                    page: page,
                    onSetPage: (e, p) => setPage(p),
                    onPerPageSelect: (e, pp) => setPerPage(pp)
                }}
            />
            <ListReportsModal
                isOpen={tableReportSummary !== undefined}
                onClose={() => setTableReportGroup(undefined)}
                summary={tableReportSummary}
                onReload={() => setTableReportsReloadCounter(tableReportsReloadCounter + 1)}
            />
        </>
    )
}
