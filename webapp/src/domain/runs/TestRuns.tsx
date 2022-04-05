import { useState, useMemo, useEffect } from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"
import { useDispatch } from "react-redux"
import {
    Button,
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
    Checkbox,
} from "@patternfly/react-core"
import { ArrowRightIcon, TrashIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import { Duration } from "luxon"
import { toEpochMillis, interleave, noop } from "../../utils"

import { byTest } from "./actions"
import * as selectors from "./selectors"
import { teamsSelector, teamToName } from "../../auth"
import { alertAction } from "../../alerts"

import { fetchTest } from "../tests/actions"
import { get } from "../tests/selectors"

import AccessIcon from "../../components/AccessIcon"
import Table from "../../components/Table"
import TagsSelect, { SelectedTags } from "../../components/TagsSelect"
import {
    CellProps,
    UseTableOptions,
    UseRowSelectInstanceProps,
    UseRowSelectRowProps,
    Column,
    UseSortByColumnOptions,
} from "react-table"
import { Run, RunsDispatch } from "./reducers"
import { Description, ExecutionTime, Menu, RunTags } from "./components"

type C = CellProps<Run> & UseTableOptions<Run> & UseRowSelectInstanceProps<Run> & { row: UseRowSelectRowProps<Run> }

type RunColumn = Column<Run> & UseSortByColumnOptions<Run>

const tableColumns: RunColumn[] = [
    {
        Header: "",
        id: "selection",
        disableSortBy: true,
        Cell: ({ row, selectedFlatRows }: C) => {
            const props = row.getToggleRowSelectedProps()
            delete props.indeterminate
            return <input type="checkbox" {...props} disabled={!row.isSelected && selectedFlatRows.length >= 2} />
        },
    },
    {
        Header: "Id",
        accessor: "id",
        Cell: (arg: C) => {
            const {
                cell: { value },
            } = arg
            return (
                <>
                    <NavLink to={`/run/${value}`}>
                        <ArrowRightIcon />
                        {"\u00A0"}
                        {value}
                    </NavLink>
                    {arg.row.original.trashed && <TrashIcon style={{ fill: "#888", marginLeft: "10px" }} />}
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
        accessor: (run: Run) =>
            Duration.fromMillis(toEpochMillis(run.stop) - toEpochMillis(run.start)).toFormat("hh:mm:ss.SSS"),
    },
    {
        Header: "Schema",
        accessor: "schema",
        disableSortBy: true,
        Cell: (arg: C) => {
            const {
                cell: { value },
            } = arg
            // LEFT JOIN results in schema.id == 0
            if (value) {
                return interleave(
                    Object.keys(value).map((key, i) => (
                        <NavLink key={2 * i} to={`/schema/${key}`}>
                            {value[key]}
                        </NavLink>
                    )),
                    i => <br key={2 * i + 1} />
                )
            } else {
                return "--"
            }
        },
    },
    {
        Header: "Tags",
        accessor: "tags",
        disableSortBy: true,
        Cell: (arg: C) => RunTags(arg.cell.value),
    },
    {
        Header: "Description",
        accessor: "description",
        Cell: (arg: C) => Description(arg.cell.value),
    },
    {
        Header: "Datasets",
        accessor: "datasets",
        Cell: (arg: C) => arg.cell.value.length,
    },
    {
        Header: "Actions",
        id: "actions",
        accessor: "id",
        disableSortBy: true,
        Cell: (arg: CellProps<Run, number>) => Menu(arg.row.original),
    },
]

export default function TestRuns() {
    const { testId: stringTestId } = useParams<any>()
    const testId = parseInt(stringTestId)

    const test = useSelector(get(testId))
    const [selectedRows, setSelectedRows] = useState<Record<string, boolean>>({})
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("start")
    const [direction, setDirection] = useState("Descending")
    const [tags, setTags] = useState<SelectedTags>()
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])

    const dispatch = useDispatch<RunsDispatch>()
    const [showTrashed, setShowTrashed] = useState(false)
    const runs = useSelector(selectors.testRuns(testId, pagination, showTrashed))
    const runCount = useSelector(selectors.count)
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        dispatch(fetchTest(testId)).catch(noop)
    }, [dispatch, testId, teams])
    useEffect(() => {
        dispatch(byTest(testId, pagination, showTrashed, tags?.toString() || "")).catch(noop)
    }, [dispatch, showTrashed, page, perPage, sort, direction, tags, pagination, testId])
    useEffect(() => {
        document.title = (test ? test.name : "Loading...") + " | Horreum"
    }, [test])
    const isLoading = useSelector(selectors.isLoading)

    const compareUrl = test ? test?.compareUrl : undefined
    const [actualCompareUrl, compareError] = useMemo(() => {
        if (compareUrl && typeof compareUrl === "function") {
            try {
                const rows = Object.keys(selectedRows).map(id => (runs ? runs[parseInt(id)].id : []))
                if (rows.length >= 2) {
                    return [compareUrl(rows), undefined]
                }
            } catch (e) {
                return [undefined, e]
            }
        }
        return [undefined, undefined]
    }, [compareUrl, runs, selectedRows])
    const hasError = !!compareError
    useEffect(() => {
        if (compareError) {
            dispatch(alertAction("COMPARE_FAILURE", "Compare function failed", compareError))
        }
    }, [hasError, compareError, dispatch])

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
                                <NavLink className="pf-c-button pf-m-secondary" to={`/run/dataset/list/${testId}`}>
                                    View datasets
                                </NavLink>
                            </ToolbarItem>
                        </ToolbarGroup>
                        {test && test.compareUrl && (
                            <ToolbarGroup>
                                <ToolbarItem>
                                    <Button
                                        variant="primary"
                                        component="a"
                                        target="_blank"
                                        href={actualCompareUrl || ""}
                                        isDisabled={!actualCompareUrl}
                                    >
                                        Compare runs
                                    </Button>
                                </ToolbarItem>
                            </ToolbarGroup>
                        )}
                        <ToolbarGroup>
                            <ToolbarItem>
                                <TagsSelect
                                    testId={testId}
                                    selection={tags}
                                    onSelect={setTags}
                                    addAllTagsOption={true}
                                    includeTrashed={showTrashed}
                                />
                            </ToolbarItem>
                            <ToolbarItem>
                                <Checkbox
                                    id="showTrashed"
                                    aria-label="show trashed runs"
                                    label="Show trashed runs"
                                    isChecked={showTrashed}
                                    onChange={setShowTrashed}
                                />
                            </ToolbarItem>
                        </ToolbarGroup>
                    </Toolbar>
                    <Pagination
                        itemCount={runCount}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </CardHeader>
                <CardBody style={{ overflowX: "auto" }}>
                    <Table
                        columns={tableColumns}
                        data={runs || []}
                        sortBy={[{ id: sort, desc: direction === "Descending" }]}
                        onSortBy={order => {
                            if (order.length > 0 && order[0]) {
                                setSort(order[0].id)
                                setDirection(order[0].desc ? "Descending" : "Ascending")
                            }
                        }}
                        isLoading={isLoading}
                        selected={selectedRows}
                        onSelected={setSelectedRows}
                    />
                </CardBody>
                <CardFooter style={{ textAlign: "right" }}>
                    <Pagination
                        itemCount={runCount}
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
