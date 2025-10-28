import {useState, useMemo, useEffect, useContext} from "react"
import { useParams } from "react-router-dom"
import {
    Button,
    Checkbox,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    SplitItem,
    Split,
} from "@patternfly/react-core"
import { ArrowRightIcon, TrashIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import { Duration } from "luxon"
import { toEpochMillis, noop } from "../../utils"

import { teamToName } from "../../utils"

import { fetchTest } from "../../api"

import {runApi, RunSummary, SortDirection, Test} from "../../api"
import { NoSchemaInRun } from "./NoSchema"
import { Description, ExecutionTime, Menu } from "./components"
import SchemaList from "./SchemaList"
import AccessIcon from "../../components/AccessIcon"
import {AppContext} from "../../context/AppContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {RunImportModal} from "./RunImportModal";
import CustomTable from "../../components/CustomTable"
import { ColumnDef, ColumnSort, createColumnHelper } from "@tanstack/react-table"
import {AuthBridgeContext} from "../../context/AuthBridgeContext";
import {AuthContextType} from "../../context/@types/authContextTypes";

const columnHelper = createColumnHelper<RunSummary>()

export default function RunList() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { isAuthenticated, teams } = useContext(AuthBridgeContext) as AuthContextType;
    const { testId } = useParams()
    const testIdInt = parseInt(testId ?? "-1")

    const [test, setTest] = useState<Test | undefined>(undefined)
    const [selectedRows, setSelectedRows] = useState<Record<string, boolean>>({})
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [sortBy, setSortBy] = useState<ColumnSort>({id: 'start', desc: true})
    const pagination = useMemo(() => ({ page, perPage, sortBy }), [page, perPage, sortBy])

    const [showTrashed, setShowTrashed] = useState(false)
    const [runs, setRuns] = useState<RunSummary[] >([])
    const [runCount, setRunCount] =useState(0)
    const [isLoading, setIsLoading] = useState(false)
    const [showNewRunModal, setShowNewRunModal] = useState(false)

    const loadTestRuns = () => {
        setIsLoading(true)
        const direction = pagination.sortBy.desc ? SortDirection.Descending : SortDirection.Ascending
        runApi.listTestRuns(
            testIdInt,
            showTrashed,
            pagination.perPage,
            pagination.page,
            pagination.sortBy.id,
            direction
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
    }, [test, showTrashed, page, perPage, sortBy, pagination, testIdInt])
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
    const tableColumns: ColumnDef<RunSummary, any>[] = [
        columnHelper.display({
            id: 'selected',
            cell: ({ row }) => <input type="checkbox" disabled={!row.getCanSelect()} checked={row.getIsSelected()} onChange={row.getToggleSelectedHandler()}/>
        }),
        columnHelper.accessor('id', {
            header: 'Id',
            cell: ({ row }) => <>
                <NavLink to={`/run/${row.original.id}#run`}>
                    <ArrowRightIcon />&nbsp;{row.original.id}
                </NavLink>
                {row.original.trashed && <TrashIcon style={{ fill: "#888", marginLeft: "10px" }} />}
            </>
        }),
        columnHelper.accessor('schemas', {
            header: 'Schemas(s)',
            enableSorting: false,
            cell: ({ row }) => {
                const value = row.original.schemas;
                // LEFT JOIN results in schema.id == 0
                return !value || Object.keys(value).length === 0 ? (
                    <NoSchemaInRun />
                ) : (
                    <SchemaList schemas={value} validationErrors={row.original.validationErrors ?? []} />
                )
            }
        }),
        columnHelper.accessor('description', {
            header: 'Description',
            cell: ({ row }) => Description(row.original.description ?? "")
        }),
        columnHelper.accessor('start', {
            header: 'Executed',
            cell: ({ row }) => ExecutionTime(row.original)
        }),
        columnHelper.accessor((run) => toEpochMillis(run.stop) - toEpochMillis(run.start), {
            header: 'Duration',
            id: "(stop - start)",
            cell: ({ getValue }) => Duration.fromMillis(getValue()).toFormat("hh:mm:ss.SSS")
        }),
        columnHelper.accessor((run) => run.datasets.length, {
            header: 'Datasets',
            id: 'datasets'
        }),
        columnHelper.accessor('owner', {
            header: 'Owner',
            cell: ({ row }) => <>
                {teamToName(row.original.owner)}&nbsp;<AccessIcon access={row.original.access} showText={false} />
            </>
        }),
        columnHelper.display({
            header: 'Actions',
            id: 'actions',
            cell: ({ row }) => Menu(row.original, loadTestRuns, clearCallback)
        })
    ]

    const toggleNewRunModal = () => {
        setShowNewRunModal(!showNewRunModal);
    };

    return (
        <>
            <Toolbar className="pf-v6-u-justify-content-space-between" style={{ width: "100%" }}>
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
                    {isAuthenticated &&
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
                    }
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
            <CustomTable<RunSummary>
                columns={tableColumns}
                data={runs || []}
                sortBy={[sortBy]}
                onSortBy={order => {
                    if (order.length > 0 && order[0]) {
                        setSortBy(order[0])
                    }
                }}
                isLoading={isLoading}
                selected={selectedRows}
                onSelected={setSelectedRows}
                pagination={{
                    top: true,
                    bottom: true,
                    count: runCount,
                    perPage: perPage,
                    page: page,
                    onSetPage: (e, p) => setPage(p),
                    onPerPageSelect: (e, pp) => setPerPage(pp)
                }}
            />
            <RunImportModal isOpen={showNewRunModal} onClose={toggleNewRunModal} test={test} owner={test?.owner || ""}/>
        </>
    )
}
