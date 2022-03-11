import { useState, useMemo, useEffect } from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import {
    Card,
    CardHeader,
    CardBody,
    CardFooter,
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
import { toEpochMillis, interleave, noop } from "../../utils"

import { dispatchError } from "../../alerts"
import { teamsSelector, teamToName } from "../../auth"

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
import { listTestDatasets, Dataset, DatasetList } from "./api"
import { Description, ExecutionTime } from "./components"
import { TestDispatch } from "../tests/reducers"
import SchemaLink from "../schemas/SchemaLink"

type C = CellProps<Dataset> &
    UseTableOptions<Dataset> &
    UseRowSelectInstanceProps<Dataset> & { row: UseRowSelectRowProps<Dataset> }

type DatasetColumn = Column<Dataset> & UseSortByColumnOptions<Dataset>

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
        accessor: (dataset: Dataset) =>
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
            if (value) {
                const schemas = value as string[]
                return interleave(
                    schemas.map((uri, i) => <SchemaLink key={2 * i} uri={uri} />),
                    i => <br key={2 * i + 1} />
                )
            } else {
                return "--"
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
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("start")
    const [direction, setDirection] = useState("Descending")
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])

    const dispatch = useDispatch<TestDispatch>()
    const [loading, setLoading] = useState(false)
    const [datasets, setDatasets] = useState<DatasetList>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(fetchTest(testId)).catch(noop)
    }, [dispatch, testId, teams])
    useEffect(() => {
        setLoading(true)
        listTestDatasets(testId, pagination)
            .then(setDatasets, error =>
                dispatchError(dispatch, error, "FETCH_DATASETS", "Failed to fetch datasets in test " + testId).catch(
                    noop
                )
            )
            .finally(() => setLoading(false))
    }, [dispatch, testId, pagination])
    useEffect(() => {
        document.title = (test ? test.name : "Loading...") + " | Horreum"
    }, [test])
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
                                <Title headingLevel="h2">Test: {`${(test && test.name) || testId}`}</Title>
                            </ToolbarItem>
                            <ToolbarItem>
                                <NavLink className="pf-c-button pf-m-primary" to={`/test/${testId}`}>
                                    Edit test
                                </NavLink>
                                <NavLink className="pf-c-button pf-m-secondary" to={`/run/list/${testId}`}>
                                    View runs
                                </NavLink>
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
                <CardBody style={{ overflowX: "auto" }}>
                    <Table
                        columns={staticColumns}
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
