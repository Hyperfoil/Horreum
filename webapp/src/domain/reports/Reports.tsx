import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"

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

import Table from "../../components/Table"
import TeamSelect, { Team, SHOW_ALL } from "../../components/TeamSelect"
import TestSelect, { SelectedTest } from "../../components/TestSelect"
import { alertAction } from "../../alerts"
import { formatDateTime } from "../../utils"

import { AllTableReports, TableReportSummary, getTableReports } from "./api"
import ButtonLink from "../../components/ButtonLink"
import { useTester } from "../../auth"

import ListReportsModal from "./ListReportsModal"

type C = CellProps<TableReportSummary>

type ReportGroup = {
    testId: number
    title: string
}

export default function Reports() {
    document.title = "Reports | Horreum"

    const dispatch = useDispatch()
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("title")
    const [direction, setDirection] = useState("Descending")
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])
    const [roles, setRoles] = useState<Team>()
    const [test, setTest] = useState<SelectedTest>()
    const [folder, setFolder] = useState<string>()

    const [tableReports, setTableReports] = useState<AllTableReports>()
    const [tableReportsReloadCounter, setTableReportsReloadCounter] = useState(0)
    const [loading, setLoading] = useState(false)

    const [tableReportGroup, setTableReportGroup] = useState<ReportGroup>()

    useEffect(() => {
        setLoading(true)
        getTableReports(pagination, (test && test.id) || undefined, roles?.key, folder)
            .then(setTableReports)
            .catch(error => dispatch(alertAction("FETCH_REPORTS", "Failed to fetch reports", error)))
            .finally(() => setLoading(false))
    }, [pagination, roles, test, folder, dispatch, tableReportsReloadCounter])

    const columns: Column<TableReportSummary>[] = useMemo(
        () => [
            {
                Header: "Title",
                id: "title",
                accessor: r => (r.reports && r.reports.length > 0 ? r.reports[0].configId : undefined),
                Cell: (arg: C) => {
                    const {
                        cell: { value: configId },
                    } = arg
                    return configId === undefined ? (
                        arg.row.original.title
                    ) : (
                        <NavLink to={`/reports/table/config/${configId}`}>
                            {arg.row.original.title} <EditIcon />
                        </NavLink>
                    )
                },
            },
            {
                Header: "Test",
                id: "testname",
                accessor: r => r.testId,
                Cell: (arg: C) => {
                    const {
                        cell: { value: testId },
                    } = arg
                    return testId !== undefined && testId >= 0 ? (
                        <NavLink to={`/test/${testId}`}>{arg.row.original.testName}</NavLink>
                    ) : (
                        "<deleted test>"
                    )
                },
            },
            {
                Header: "Last report",
                id: "last report",
                accessor: "reports",
                disableSortBy: true, // TODO: fix client-side sorting
                Cell: (arg: C) => {
                    const reports = arg.cell.value
                    if (reports && reports.length > 0) {
                        const last = reports[0]
                        return (
                            <NavLink to={`/reports/table/${last.id}`}>
                                <ArrowRightIcon />
                                {"\u00A0"}
                                {formatDateTime(last.created * 1000)}
                            </NavLink>
                        )
                    } else {
                        return "No report"
                    }
                },
            },
            {
                Header: "Total reports",
                id: "report count",
                accessor: "reports",
                disableSortBy: true, // TODO: fix client-side sorting
                Cell: (arg: C) => {
                    return (
                        <Button
                            variant="link"
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
        <PageSection>
            <Card>
                <CardHeader>
                    <Flex style={{ width: "100%" }}>
                        {isTester && (
                            <FlexItem>
                                <ButtonLink to="/reports/table/config/__new">New report configuration</ButtonLink>
                            </FlexItem>
                        )}
                        <FlexItem>
                            <TeamSelect includeGeneral={true} selection={roles || SHOW_ALL} onSelect={setRoles} />
                        </FlexItem>
                        <FlexItem>
                            <TestSelect
                                selection={test}
                                onSelect={(test, folder) => {
                                    setTest(test)
                                    setFolder(folder)
                                }}
                                extraOptions={[{ id: 0, toString: () => "All tests" }]}
                            />
                        </FlexItem>
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
            </Card>
            <ListReportsModal
                isOpen={tableReportSummary !== undefined}
                onClose={() => setTableReportGroup(undefined)}
                summary={tableReportSummary}
                onReload={() => setTableReportsReloadCounter(tableReportsReloadCounter + 1)}
            />
        </PageSection>
    )
}
