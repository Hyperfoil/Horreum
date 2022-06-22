import { useCallback, useEffect, useMemo, useState } from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import {
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    ExpandableSection,
    ExpandableSectionToggle,
    PageSection,
    Pagination,
    Title,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
} from "@patternfly/react-core"
import { ArrowRightIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import { Duration } from "luxon"
import { toEpochMillis, interleave, noop, fingerprintToString } from "../../utils"

import { dispatchError } from "../../alerts"
import { teamsSelector, teamToName, tokenSelector } from "../../auth"

import { fetchTest } from "../tests/actions"
import { get } from "../tests/selectors"

import AccessIcon from "../../components/AccessIcon"
import Table from "../../components/Table"
import {
    CellProps,
    UseTableOptions,
    UseRowSelectInstanceProps,
    UseRowSelectRowProps,
    Column,
    UseSortByColumnOptions,
} from "react-table"
import Api, { DatasetSummary, DatasetList } from "../../api"
import { Description, ExecutionTime, renderCell } from "./components"
import { TestDispatch } from "../tests/reducers"
import SchemaLink from "../schemas/SchemaLink"
import { NoSchemaInDataset } from "./NoSchema"
import LabelsSelect, { SelectedLabels } from "../../components/LabelsSelect"

type C = CellProps<DatasetSummary> &
    UseTableOptions<DatasetSummary> &
    UseRowSelectInstanceProps<DatasetSummary> & { row: UseRowSelectRowProps<DatasetSummary> }

type DatasetColumn = Column<DatasetSummary> & UseSortByColumnOptions<DatasetSummary>

const staticColumns: DatasetColumn[] = [
    {
        Header: "",
        id: "selection",
        disableSortBy: true,
        Cell: ({ row }: C) => {
            const props = row.getToggleRowSelectedProps()
            delete props.indeterminate
            return <input type="checkbox" {...props} />
        },
    },
    {
        Header: "Run",
        accessor: "runId",
        Cell: (arg: C) => {
            const {
                cell: { value },
            } = arg
            return (
                <>
                    <NavLink to={`/run/${value}#dataset${arg.row.original.ordinal}`}>
                        <ArrowRightIcon />
                        {`\u00A0${value} #${arg.row.original.ordinal + 1}`}
                    </NavLink>
                </>
            )
        },
    },
    {
        Header: "Access",
        accessor: "access",
        Cell: (arg: C) => <AccessIcon access={arg.cell.value} />,
    },
    {
        Header: "Owner",
        accessor: "owner",
        Cell: (arg: C) => teamToName(arg.cell.value),
    },
    {
        Header: "Executed",
        accessor: "start",
        Cell: (arg: C) => ExecutionTime(arg.row.original),
    },
    {
        Header: "Duration",
        id: "(stop - start)",
        accessor: dataset =>
            Duration.fromMillis(toEpochMillis(dataset.stop) - toEpochMillis(dataset.start)).toFormat("hh:mm:ss.SSS"),
    },
    {
        Header: "Schema",
        accessor: "schemas",
        disableSortBy: true,
        Cell: (arg: C) => {
            const {
                cell: { value },
            } = arg
            // LEFT JOIN results in schema.id == 0
            if (!value || (value as string[]).length == 0) {
                return <NoSchemaInDataset />
            } else {
                const schemas = value as string[]
                return interleave(
                    schemas.map((uri, i) => <SchemaLink key={2 * i} uri={uri} />),
                    i => <br key={2 * i + 1} />
                )
            }
        },
    },
    {
        Header: "Description",
        accessor: "description",
        Cell: (arg: C) => Description(arg.cell.value),
    },
]

export default function TestDatasets() {
    const { testId: stringTestId } = useParams<any>()
    const testId = parseInt(stringTestId)

    const test = useSelector(get(testId))
    const [selectedRows, setSelectedRows] = useState<Record<string, boolean>>({})
    const [filter, setFilter] = useState<SelectedLabels>()
    const [filterExpanded, setFilterExpanded] = useState(false)
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("start")
    const [direction, setDirection] = useState("Descending")
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])

    const dispatch = useDispatch<TestDispatch>()
    const [loading, setLoading] = useState(false)
    const [datasets, setDatasets] = useState<DatasetList>()
    const teams = useSelector(teamsSelector)
    const token = useSelector(tokenSelector)
    useEffect(() => {
        dispatch(fetchTest(testId)).catch(noop)
    }, [dispatch, testId, teams, token])
    useEffect(() => {
        setLoading(true)
        Api.datasetServiceListByTest(
            testId,
            pagination.direction,
            fingerprintToString(filter),
            pagination.perPage,
            pagination.page,
            pagination.sort
        )
            .then(setDatasets, error =>
                dispatchError(dispatch, error, "FETCH_DATASETS", "Failed to fetch datasets in test " + testId).catch(
                    noop
                )
            )
            .finally(() => setLoading(false))
    }, [dispatch, testId, filter, pagination, teams])
    useEffect(() => {
        document.title = (test?.name || "Loading...") + " | Horreum"
    }, [test])
    useEffect(() => {
        console.log(filter)
    }, [filter])
    const columns = useMemo(() => {
        const allColumns = [...staticColumns]
        const components = test?.defaultView?.components || []
        components.forEach(vc => {
            allColumns.push({
                Header: vc.headerName,
                accessor: dataset => dataset.view && dataset.view[vc.id],
                // In general case we would have to calculate the final sortable cell value
                // in database, or fetch all runs and sort in server doing the rendering
                disableSortBy: (!!vc.render && vc.render !== "") || vc.labels.length > 1,
                id: test ? "view_data:" + vc.id + ":" + vc.labels[0] : undefined,
                Cell: renderCell(vc.render, vc.labels.length == 1 ? vc.labels[0] : undefined, token),
            })
        })
        return allColumns
    }, [test, token])
    const labelsSource = useCallback(() => Api.testServiceListLabelValues(testId, true, false), [testId, teams, token])
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Toolbar
                        className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                        style={{ width: "70%", display: "flex" }}
                    >
                        <ToolbarGroup style={{ flexGrow: 100 }}>
                            <ToolbarItem>
                                <Title headingLevel="h2">Test: {`${test?.name || testId}`}</Title>
                            </ToolbarItem>
                            <ToolbarItem>
                                <NavLink className="pf-c-button pf-m-primary" to={`/test/${testId}`}>
                                    Edit test
                                </NavLink>
                                <NavLink className="pf-c-button pf-m-secondary" to={`/run/list/${testId}`}>
                                    View runs
                                </NavLink>
                            </ToolbarItem>
                            <ToolbarItem>
                                <ExpandableSectionToggle
                                    isExpanded={filterExpanded}
                                    contentId="filter"
                                    onToggle={setFilterExpanded}
                                >
                                    {filterExpanded ? "Hide filters" : "Show filters"}
                                </ExpandableSectionToggle>
                            </ToolbarItem>
                        </ToolbarGroup>
                    </Toolbar>
                    <Pagination
                        itemCount={datasets?.total}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardHeader>
                <CardHeader>
                    <ExpandableSection isDetached isExpanded={filterExpanded} contentId="filter">
                        <LabelsSelect
                            forceSplit={true}
                            fireOnPartial={true}
                            showKeyHelper={true}
                            selection={filter}
                            onSelect={setFilter}
                            source={labelsSource}
                            emptyPlaceholder={<span>No filters available</span>}
                        />
                    </ExpandableSection>
                </CardHeader>
                <CardBody style={{ overflowX: "auto" }}>
                    <Table
                        columns={columns}
                        data={datasets?.datasets || []}
                        sortBy={[{ id: sort, desc: direction === "Descending" }]}
                        onSortBy={order => {
                            if (order.length > 0 && order[0]) {
                                setSort(order[0].id)
                                setDirection(order[0].desc ? "Descending" : "Ascending")
                            }
                        }}
                        isLoading={loading}
                        selected={selectedRows}
                        onSelected={setSelectedRows}
                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={datasets?.total}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardFooter>
            </Card>
        </PageSection>
    )
}
