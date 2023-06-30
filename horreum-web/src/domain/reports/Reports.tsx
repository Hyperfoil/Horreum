import {useContext, useEffect, useMemo, useState} from "react"
import { useSelector } from "react-redux"

import { NavLink } from "react-router-dom"
import { CellProps, Column } from "react-table"
import {
    Button,
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    Flex,
    FlexItem,
    PageSection,
    Pagination,
} from "@patternfly/react-core"
import { ArrowRightIcon, FolderOpenIcon, EditIcon } from "@patternfly/react-icons"

import ImportButton from "../../components/ImportButton"
import Table from "../../components/Table"
import { Team } from "../../components/TeamSelect"
import { SelectedTest } from "../../components/TestSelect"
import { formatDateTime } from "../../utils"

import {AllTableReports, reportApi, SortDirection, TableReportSummary} from "../../api"
import ButtonLink from "../../components/ButtonLink"
import { useTester, teamsSelector } from "../../auth"

import ListReportsModal from "./ListReportsModal"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type C = CellProps<TableReportSummary>

type ReportGroup = {
    testId: number
    title?: string
}


export default function Reports(props: ReportGroup) {
    document.title = "Reports | Horreum"

    const { alerting } = useContext(AppContext) as AppContextType;
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("title")
    const [direction, setDirection] = useState<SortDirection>("Descending")
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])
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
            pagination.direction,
            undefined,
            pagination.perPage,
            pagination.page,
            roles?.key,
            pagination.sort,
            (test && test.id) || undefined
        )
            .then(setTableReports)
            .catch(error => alerting.dispatchError(error, "FETCH_REPORTS", "Failed to fetch reports"))
            .finally(() => setLoading(false))
    }, [pagination, roles, teams, tableReportsReloadCounter])

    const columns: Column<TableReportSummary>[] = useMemo(
        () => [
            {
                Header: "Title",
                id: "title",
                accessor: r => r.title.toLowerCase(), // for case-insensitive sorting
                Cell: (arg: C) => {
                    const title = arg.row.original.title
                    const configId = arg.row.original.configId

                    return configId === undefined ? (
                        title
                    ) : (
                        <NavLink to={`/test/${test.id}/reports/table/config/${configId}`}>
                            {title} <EditIcon />
                        </NavLink>
                    )
                },
            },
            {
                Header: "Last report",
                id: "created",
                accessor: "reports",
                disableSortBy: true, // TODO: fix client-side sorting
                Cell: (arg: C) => {
                    const reports = arg.cell.value
                    if (reports && reports.length > 0) {
                        const last = reports[0]
                        return (
                            <NavLink to={`/test/${test.id}/reports/table/${last.id}`}>
                                <ArrowRightIcon />
                                {"\u00A0"}
                                {formatDateTime(last.created)}
                            </NavLink>
                        )
                    } else {
                        return "No report"
                    }
                },
            },
            {
                Header: "Total reports",
                id: "count",
                accessor: "reports",
                disableSortBy: true, // TODO: fix client-side sorting
                Cell: (arg: C) => {
                    if (arg.cell.value.length === 0) {
                        return <span style={{ paddingLeft: "16px" }}>0</span>
                    }
                    return (
                        <Button
                            variant="link"
                            style={{ paddingTop: 0, paddingBottom: 0 }}
                            onClick={() =>
                                setTableReportGroup({ testId: arg.row.original.testId, title: arg.row.original.title })
                            }
                        >
                            {arg.cell.value.length}
                            {"\u00A0"}
                            <FolderOpenIcon />
                        </Button>
                    )
                },
            },
        ],
        []
    )

    const isTester = useTester()
    const tableReportSummary =
        (tableReportGroup !== undefined &&
            tableReports?.reports.find(
                summary => summary.testId === tableReportGroup.testId && summary.title === tableReportGroup.title
            )) ||
        undefined
    return (
            <Card>
                <CardHeader>
                    <Flex style={{ width: "100%" }}>
                        {isTester && (
                            <FlexItem>
                                <ButtonLink to={`/test/${test.id}/reports/table/config/__new`}>New report configuration</ButtonLink>
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
                                    onImport={config => reportApi.importTableReportConfig(config)}
                                    onImported={() => setTableReportsReloadCounter(tableReportsReloadCounter + 1)}
                                />
                            </FlexItem>
                        )}
                        <FlexItem grow={{ default: "grow" }}>{"\u00A0"}</FlexItem>
                        <FlexItem>
                            <Pagination
                                itemCount={tableReports?.count || 0}
                                perPage={perPage}
                                page={page}
                                onSetPage={(e, p) => setPage(p)}
                                onPerPageSelect={(e, pp) => setPerPage(pp)}
                            />
                        </FlexItem>
                    </Flex>
                </CardHeader>
                <CardBody style={{ overflowX: "auto" }}>
                    <Table
                        columns={columns}
                        data={tableReports?.reports || []}
                        onSortBy={order => {
                            if (order.length > 0 && order[0]) {
                                setSort(order[0].id)
                                setDirection(order[0].desc ? "Descending" : "Ascending")
                            }
                        }}
                        isLoading={loading}
                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={tableReports?.count || 0}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardFooter>
                <ListReportsModal
                    isOpen={tableReportSummary !== undefined}
                    onClose={() => setTableReportGroup(undefined)}
                    summary={tableReportSummary}
                    onReload={() => setTableReportsReloadCounter(tableReportsReloadCounter + 1)}
                />
            </Card>
    )
}
