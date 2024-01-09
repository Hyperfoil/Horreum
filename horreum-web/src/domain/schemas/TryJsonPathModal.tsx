import {useContext, useEffect, useMemo, useState} from "react"

import { Bullseye, Button, Flex, FlexItem, Modal, Pagination, Radio, Spinner, TextInput } from "@patternfly/react-core"
import { Table, TableBody, TableHeader } from "@patternfly/react-table"
import { NavLink } from "react-router-dom"

import JsonPathDocsLink from "../../components/JsonPathDocsLink"
import Editor from "../../components/Editor/monaco/Editor"
import {datasetApi, DatasetSummary, QueryResult, runApi, RunSummary, SortDirection, sqlApi} from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

export type JsonPathTarget = "run" | "dataset"

type TryJsonPathModalProps = {
    uri: string
    target: JsonPathTarget
    jsonpath?: string
    array: boolean
    onChange(jsonpath: string, array: boolean): void
    onClose(): void
}

export default function TryJsonPathModal(props: TryJsonPathModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [runs, setRuns] = useState<RunSummary[]>()
    const [datasets, setDatasets] = useState<DatasetSummary[]>()
    const [count, setCount] = useState(0) // total runs/datasets, not runs.length
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [valid, setValid] = useState(true)
    const [result, setResult] = useState<string>()
    const [target, setTarget] = useState<RunSummary | DatasetSummary>()
    const pagination = useMemo(() => ({ page, perPage, sort: "start", direction: "Descending" }), [page, perPage])
    useEffect(() => {
        if (!props.jsonpath) {
            return
        }
        if (props.target === "run") {
            runApi.listBySchema(
                props.uri,
                pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
                pagination.perPage,
                pagination.page,
                pagination.sort
            ).then(
                summary => {
                    setRuns(summary.runs)
                    setCount(summary.total)
                },
                error => {
                    alerting.dispatchError(error,"FETCH_RUNS_BY_URI", "Failed to fetch runs by Schema URI.")
                    props.onClose()
                }
            )
        } else {
            // target === dataset
            datasetApi.listBySchema(
                props.uri,
                pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
                pagination.perPage,
                pagination.page,
                pagination.sort
            ).then(
                response => {
                    setDatasets(response.datasets)
                    setCount(response.total)
                },
                error => {
                    alerting.dispatchError(error,"FETCH_DATASETS_BY_URI", "Failed to fetch datasets by Schema URI.")
                    props.onClose()
                }
            )
        }
    }, [props.uri, props.jsonpath, pagination, props.onClose])
    const executeQuery = (id: number) => {
        if (!props.jsonpath) {
            return ""
        }
        let response: Promise<QueryResult>
        if (props.target === "run") {
            response = sqlApi.queryRunData(id, props.jsonpath, props.array, props.uri)
        } else {
            response = sqlApi.queryDatasetData(id, props.jsonpath, props.array, props.uri)
        }
        return response.then(
            result => {
                setValid(result.valid)
                if (result.valid) {
                    try {
                        result.value = result.value ? JSON.parse(result.value) : null
                    } catch (e) {
                        // ignored
                    }
                    setResult(JSON.stringify(result.value, null, 2))
                } else {
                    setResult(result.reason)
                }
            },
            error => {
                setValid(false)
                setResult(error)
            }
        )
    }
    const updateQuery = (jsonpath: string | undefined, array: boolean) => {
        setValid(true)
        setResult(undefined)
        setTarget(undefined)
        props.onChange(jsonpath || "", array)
    }
    return (
        <Modal
            variant="large"
            title={`Execute selected JsonPath on one of these ${props.target}s`}
            isOpen={props.jsonpath !== undefined}
            onClose={() => {
                setRuns(undefined)
                setPage(1)
                setPerPage(20)
                setResult(undefined)
                setTarget(undefined)
                props.onClose()
            }}
        >
            <Flex alignItems={{ default: "alignItemsCenter" }}>
                <FlexItem>
                    <JsonPathDocsLink />
                </FlexItem>
                <FlexItem grow={{ default: "grow" }}>
                    <TextInput
                        id="jsonpath"
                        value={props.jsonpath || ""}
                        onChange={value => updateQuery(value, props.array)}
                        validated={valid ? "default" : "error"}
                    />
                </FlexItem>
                <FlexItem>
                    <Radio
                        id="first"
                        label="First match"
                        name="variant"
                        isChecked={!props.array}
                        onChange={checked => updateQuery(props.jsonpath, !checked)}
                    />
                    <Radio
                        id="all"
                        label="All matches"
                        name="variant"
                        isChecked={props.array}
                        onChange={checked => updateQuery(props.jsonpath, checked)}
                    />
                </FlexItem>
            </Flex>
            {runs === undefined && datasets === undefined && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {(runs || datasets) && result === undefined && (
                <>
                    {/* TODO FIXME */}
                    <div style={{ display: "block", overflowY: "scroll", maxHeight: "50vh" }}>
                        {runs && (
                            <Table
                                aria-label="Available runs"
                                variant="compact"
                                cells={["Test", "Run", "Description", ""]}
                                rows={runs.map(r => ({
                                    cells: [
                                        r.testname,
                                        {
                                            title: (
                                                <NavLink
                                                    to={`/run/${r.id}?query=${encodeURIComponent(
                                                        props.jsonpath || ""
                                                    )}#run`}
                                                >
                                                    {r.id}
                                                </NavLink>
                                            ),
                                        },
                                        r.description,
                                        {
                                            title: (
                                                <Button
                                                    onClick={() => {
                                                        setTarget(r)
                                                        executeQuery(r.id)
                                                    }}
                                                >
                                                    Execute
                                                </Button>
                                            ),
                                        },
                                    ],
                                }))}
                            >
                                <TableHeader />
                                <TableBody />
                            </Table>
                        )}
                        {datasets && (
                            <Table
                                aria-label="Available datasets"
                                variant="compact"
                                cells={["Test", "Dataset", "Description", ""]}
                                rows={datasets.map(d => ({
                                    cells: [
                                        d.testname,
                                        {
                                            title: (
                                                <NavLink
                                                    to={`/run/${d.runId}?query=${encodeURIComponent(
                                                        props.jsonpath || ""
                                                    )}#dataset${d.ordinal}`}
                                                >
                                                    {d.runId}/{d.ordinal + 1}
                                                </NavLink>
                                            ),
                                        },
                                        d.description,
                                        {
                                            title: (
                                                <Button
                                                    onClick={() => {
                                                        setTarget(d)
                                                        executeQuery(d.id)
                                                    }}
                                                >
                                                    Execute
                                                </Button>
                                            ),
                                        },
                                    ],
                                }))}
                            >
                                <TableHeader />
                                <TableBody />
                            </Table>
                        )}
                    </div>
                    <Pagination
                        itemCount={count}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </>
            )}
            {result !== undefined && (
                <div style={{ minHeight: "100px", height: "250px", resize: "vertical", overflow: "auto" }}>
                    <Editor value={result} options={{ readOnly: true }} />
                </div>
            )}
            {target !== undefined && (
                <div style={{ textAlign: "right", paddingTop: "10px" }}>
                    <Button
                        onClick={() => {
                            setResult(undefined)
                            setTarget(undefined)
                        }}
                    >
                        Dismiss
                    </Button>
                    {"\u00A0"}
                    {props.target === "run" && (
                        <NavLink
                            className="pf-c-button pf-m-secondary"
                            to={`/run/${target?.id}?query=${encodeURIComponent(props.jsonpath || "")}#run`}
                        >
                            Go to run {target?.id}
                        </NavLink>
                    )}
                    {props.target === "run" && (
                        <NavLink
                            className="pf-c-button pf-m-secondary"
                            to={`/run/${(target as DatasetSummary).runId}?query=${encodeURIComponent(
                                props.jsonpath || ""
                            )}#dataset${(target as DatasetSummary).ordinal}`}
                        >
                            Go to dataset {(target as DatasetSummary).runId} #{(target as DatasetSummary).ordinal + 1}
                        </NavLink>
                    )}
                </div>
            )}
        </Modal>
    )
}
