import { useEffect, useMemo, useState } from 'react'
import { useDispatch } from 'react-redux'

import { NavLink } from 'react-router-dom'
import { CellProps, Column } from 'react-table'
import {
    Button,
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    Flex,
    FlexItem,
    Modal,
    PageSection,
    Pagination,
} from '@patternfly/react-core';
import {
    ArrowRightIcon,
    FolderOpenIcon,
    EditIcon,
} from '@patternfly/react-icons'
import {
    TableComposable,
    Thead,
    Tbody,
    Tr,
    Th,
    Td,
} from '@patternfly/react-table';


import Table from '../../components/Table';
import TeamSelect, { Team, SHOW_ALL } from '../../components/TeamSelect'
import TestSelect, { SelectedTest } from '../../components/TestSelect'
import { alertAction } from '../../alerts'
import { formatDateTime } from '../../utils'

import {
    AllTableReports,
    TableReportSummary,
    getTableReports,
} from './api'

type C = CellProps<TableReportSummary>

export default function Reports() {
    document.title = "Reports | Horreum"

    const dispatch = useDispatch();
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("test.id")
    const [direction, setDirection] = useState("Descending")
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [ page, perPage, sort, direction ])
    const [roles, setRoles] = useState<Team>()
    const [test, setTest] = useState<SelectedTest>()

    const [tableReports, setTableReports] = useState<AllTableReports>()
    const [loading, setLoading] = useState(false)

    const [tableReportConfigId, setTableReportConfigId] = useState<number>()

    useEffect(() => {
        setLoading(true)
        getTableReports(pagination, (test && test.id) || undefined, roles?.key).then(setTableReports)
            .catch(error => dispatch(alertAction('FETCH_REPORTS', "Failed to fetch reports", error)))
            .finally(() => setLoading(false))
    }, [pagination, roles, test, dispatch])

    const columns: Column<TableReportSummary>[] = useMemo(() => [
        {
            Header: "Title",
            id: "title",
            accessor: r => r.config.id,
            Cell: (arg: C) => {
                const {cell: {value: id} } = arg;
                return <NavLink to={`/reports/table/config/${id}`}>{  arg.row.original.config.title } <EditIcon /></NavLink>
            }
        }, {
            Header: "Test",
            id: "testname",
            disableSortBy: true,
            accessor: r => r.config.test.id,
            Cell: (arg: C) => {
                const {cell: { value: testid } } = arg;
                return <NavLink to={`/test/${testid}`}>{  arg.row.original.config.test.name }</NavLink>
            }
        }, {
            Header: "Last report",
            accessor: "reports",
            disableSortBy: true,
            Cell: (arg: C) => {
                const reports = arg.cell.value
                if (reports && reports.length > 0) {
                    const last = reports[reports.length - 1]
                    return (
                        <NavLink to={`/reports/table/${last.id}`}>
                            <ArrowRightIcon />{ '\u00A0' }{ formatDateTime(last.created) }
                        </NavLink>
                    )
                } else {
                    return "No report"
                }
            }
        }, {
            Header:"Total reports",
            id: "report count",
            accessor: "reports",
            disableSortBy: true,
            Cell: (arg: C) => {
                return <Button
                    variant="link"
                    onClick={ () => setTableReportConfigId(arg.row.original.config.id)}
                >
                    { arg.cell.value.length }{'\u00A0'}<FolderOpenIcon />
                </Button>
            }
        },
    ], [])

    const tableReportConfig = (tableReportConfigId !== undefined && tableReports &&
        tableReports.reports.find(summary => summary.config.id === tableReportConfigId)) || undefined;
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Flex style={{ width: "100%"}}>
                        <FlexItem>
                            <TeamSelect
                                includeGeneral={true}
                                selection={roles || SHOW_ALL}
                                onSelect={setRoles}
                            />
                        </FlexItem>
                        <FlexItem>
                            <TestSelect
                                selection={test}
                                onSelect={ setTest }
                                extraOptions={[ { id: 0, toString: () => "All tests"}]}
                            />
                        </FlexItem>
                        <FlexItem grow={{ default: "grow"}}>{'\u00A0'}</FlexItem>
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
                <CardBody style={{ overflowX: "auto"}}>
                <Table
                    columns={columns}
                    data={ tableReports?.reports || []}
                    onSortBy={ (order) => {
                        if (order.length > 0 && order[0]) {
                            setSort(order[0].id)
                            setDirection(order[0].desc ? "Descending" : "Ascending")
                        }
                    }}
                    isLoading={ loading }/>
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
            <Modal
                title={ "Reports for " + tableReportConfig?.config?.title}
                variant="small"
                isOpen={ tableReportConfigId !== undefined}
                onClose={ () => setTableReportConfigId(undefined) }
            >
                <div style={{ overflowY: 'auto'}}>
                { tableReportConfig &&
                    <TableComposable variant="compact">
                        <Thead>
                            <Tr>
                                <Th>Report</Th>
                                <Th>Created</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                        { tableReportConfig.reports.map(({ id, created }) => (
                            <Tr key={id}>
                                <Td><NavLink to={`/reports/table/${id}`}><ArrowRightIcon />{'\u00A0'}{ id }</NavLink></Td>
                                <Td>{ formatDateTime(created) }</Td>
                            </Tr>))
                        }
                        </Tbody>
                    </TableComposable>
                }
                </div>
            </Modal>
        </PageSection>
    )
}