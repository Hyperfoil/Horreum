import {useState, useMemo, useEffect, useContext} from "react"
import { useParams } from "react-router-dom"
import { useSelector } from "react-redux"
import {
    Breadcrumb,
    BreadcrumbItem,
    Button,
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    Checkbox,
    PageSection,
    Pagination,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    SplitItem,
    Split,
} from "@patternfly/react-core"
import { ArrowRightIcon, TrashIcon } from "@patternfly/react-icons"
import { Link, NavLink } from "react-router-dom"

import { Duration } from "luxon"
import { toEpochMillis, noop } from "../../utils"

import { teamsSelector, teamToName } from "../../auth"

import { fetchTest } from "../../api"

import Table from "../../components/Table"
import {
    CellProps,
    UseTableOptions,
    UseRowSelectInstanceProps,
    UseRowSelectRowProps,
    Column,
    UseSortByColumnOptions,
} from "react-table"
import {runApi, RunSummary, SortDirection, Test} from "../../api"
import { NoSchemaInRun } from "./NoSchema"
import { Description, ExecutionTime, Menu } from "./components"
import SchemaList from "./SchemaList"
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {RunImportModal} from "./RunImportModal";

type C = CellProps<RunSummary> &
    UseTableOptions<RunSummary> &
    UseRowSelectInstanceProps<RunSummary> & { row: UseRowSelectRowProps<RunSummary> }

type RunColumn = Column<RunSummary> & UseSortByColumnOptions<RunSummary>


export default function RunList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { testId } = useParams()
    const testIdInt = parseInt(testId ?? "-1")

    const [test, setTest] = useState<Test | undefined>(undefined)
    const [selectedRows, setSelectedRows] = useState<Record<string, boolean>>({})
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sort, setSort] = useState("start")
    const [direction, setDirection] = useState("Descending")
    const pagination = useMemo(() => ({ page, perPage, sort, direction }), [page, perPage, sort, direction])

    const [showTrashed, setShowTrashed] = useState(false)
    const [runs, setRuns] = useState<RunSummary[] >([])
    const [runCount, setRunCount] =useState(0)
    const teams = useSelector(teamsSelector)
    const [isLoading, setIsLoading] = useState(false)
    const [showNewRunModal, setShowNewRunModal] = useState(false)

    const loadTestRuns = () => {
        setIsLoading(true)
        runApi.listTestRuns(
            testIdInt,
            showTrashed,
            pagination.perPage,
            pagination.page,
            pagination.sort,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending
        ).then(
            runsSummary => {
                setRuns(runsSummary.runs)
                setRunCount(runsSummary.total)
            },
            error => alerting.dispatchError(error,"FETCH_RUNS", "Failed to fetch runs for test " + testIdInt)
        ).finally(() => setIsLoading(false))
    }

    useEffect(() => {
        setIsLoading(true)
        fetchTest(testIdInt, alerting)
            .then(test => setTest(test))
            .catch(noop)
        setIsLoading(false)
    }, [testIdInt, teams])
    useEffect(() => {
        loadTestRuns()
    }, [test, showTrashed, page, perPage, sort, direction, pagination, testIdInt])
    useEffect(() => {
        document.title = (test?.name || "Loading...") + " | Horreum"
    }, [test])


    const [actualCompareUrl, compareError] = useMemo(() => {
        if(!test || !test.compareUrl || !runs || !selectedRows){
            return [undefined,undefined]
        }
        try{
            //need || "" because test.compareUrl could be undefined
            let compareUrl = !test.compareUrl.startsWith("http") ? `http://${test.compareUrl||""}` : test.compareUrl
            const url = new URL(compareUrl) 
            const params = url.searchParams;
            const rows = Object.keys(selectedRows).map(id => (runs ? runs[parseInt(id)].id : []))
            if(rows && rows.length > 0){
                rows.forEach(row=>params.append("id",`${row}`))
                return [url.toString(),undefined]
            }else{
                return [undefined,undefined]
            }
        }catch(e){
            return [undefined,e]
        }
    }, [runs, selectedRows, test?.compareUrl])
    const hasError = !!compareError
    useEffect(() => {
        if (compareError) {
            alerting.dispatchError(compareError,"COMPARE_FAILURE", "Compare function failed")
        }
    }, [hasError, compareError])

    const clearCallback = () => {
        setSelectedRows({})
    }
    const tableColumns: RunColumn[] = [
        {
            Header: "",
            id: "selection",
            disableSortBy: true,
            Cell: ({ row }: C) => {
                const props = row.getToggleRowSelectedProps()
                delete props.indeterminate
                // Note: to limit selection to 2 entries use
                //   disabled={!row.isSelected && selectedFlatRows.length >= 2}
                // with { row, selectedFlatRows }: C as this function's argument
                return <input type="checkbox" {...props} />
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
                        <NavLink to={`/run/${value}#run`}>
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
            Header: "Schema(s)",
            accessor: "schemas",
            disableSortBy: true,
            Cell: (arg: C) => {
                const {
                    cell: { value },
                } = arg
                // LEFT JOIN results in schema.id == 0
                if (!value || Object.keys(value).length == 0) {
                    return <NoSchemaInRun />
                } else {
                    return <SchemaList schemas={value} validationErrors={arg.row.original.validationErrors || []} />
                }
            },
        },
        {
            Header: "Description",
            accessor: "description",
            Cell: (arg: C) => Description(arg.cell.value),
        },
        {
            Header: "Executed",
            accessor: "start",
            Cell: (arg: C) => ExecutionTime(arg.row.original),
        },
        {
            Header: "Duration",
            id: "(stop - start)",
            accessor: (run: RunSummary) =>
                Duration.fromMillis(toEpochMillis(run.stop) - toEpochMillis(run.start)).toFormat("hh:mm:ss.SSS"),
        },
        {
            Header: "Datasets",
            accessor: "datasets",
            Cell: (arg: C) => arg.cell.value.length,
        },
        {
            Header: "Owner",
            id: "owner",
            accessor: (row: RunSummary) => ({
                owner: row.owner,
                access: row.access,
            }),
            Cell: (arg: C) => (
                <>
                    {teamToName(arg.cell.value.owner)}
                    <span style={{ marginLeft: '8px' }}>
                        <AccessIcon access={arg.cell.value.access} showText={false} />
                    </span>
                </>
            ),
        },
        {
            Header: "Actions",
            id: "actions",
            accessor: "id",
            disableSortBy: true,
            Cell: (arg: CellProps<RunSummary, number>) => Menu(arg.row.original, loadTestRuns, clearCallback),
        },
    ]

    const toggleNewRunModal = () => {
        setShowNewRunModal(!showNewRunModal);
    };

    return (
            <Card>
                <CardHeader>
                    <Toolbar className="pf-v5-u-justify-content-space-between" style={{ width: "100%" }}>
                        <ToolbarGroup>
                            <ToolbarItem>
                                <Checkbox
                                    id="showTrashed"
                                    aria-label="show trashed runs"
                                    label="Show trashed runs"
                                    isChecked={showTrashed}
                                    onChange={(_event, val) => setShowTrashed(!showTrashed)}
                                />
                            </ToolbarItem>
                            <ToolbarItem>
                                <Split hasGutter>
                                    <SplitItem>
                                        <Button
                                            isDisabled={false}
                                            variant="primary"
                                            onClick={toggleNewRunModal}
                                        >
                                            Import Data
                                        </Button></SplitItem>
                                </Split>
                            </ToolbarItem>
                            {test && test.compareUrl && (
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
                            )}
                        </ToolbarGroup>
                    </Toolbar>
                </CardHeader>
                <CardHeader style={{ margin: 0, display: "block", textAlign: "right" }}>
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
                <RunImportModal isOpen={showNewRunModal} onClose={toggleNewRunModal} test={test} owner={test?.owner || ""}/>
            </Card>
    )
}
