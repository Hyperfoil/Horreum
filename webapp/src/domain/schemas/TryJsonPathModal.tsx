import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"

import { Bullseye, Button, Flex, FlexItem, Modal, Pagination, Spinner, TextInput } from "@patternfly/react-core"
import { Table, TableBody, TableHeader } from "@patternfly/react-table"
import { NavLink } from "react-router-dom"

import { Dataset, listBySchema, datasetsBySchema, query, queryDataset, QueryResult } from "../runs/api"
import JsonPathDocsLink from "../../components/JsonPathDocsLink"
import Editor from "../../components/Editor/monaco/Editor"
import { Run } from "../runs/reducers"
import { alertAction } from "../../alerts"

export type JsonPathTarget = "run" | "dataset"

type TryJsonPathModalProps = {
    uri: string
    target: JsonPathTarget
    jsonpath?: string
    onChange(jsonpath: string): void
    onClose(): void
}

export default function TryJsonPathModal(props: TryJsonPathModalProps) {
    const [runs, setRuns] = useState<Run[]>()
    const [datasets, setDatasets] = useState<Dataset[]>()
    const [count, setCount] = useState(0) // total runs/datasets, not runs.length
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [valid, setValid] = useState(true)
    const [result, setResult] = useState<string>()
    const [target, setTarget] = useState<Run | Dataset>()
    const pagination = useMemo(() => ({ page, perPage, sort: "start", direction: "Descending" }), [page, perPage])
    const dispatch = useDispatch()
    useEffect(() => {
        if (!props.jsonpath) {
            return
        }
        if (props.target === "run") {
            listBySchema(props.uri, pagination).then(
                response => {
                    setRuns(response.runs)
                    setCount(response.total)
                },
                error => {
                    dispatch(alertAction("FETCH_RUNS_BY_URI", "Failed to fetch runs by Schema URI.", error))
                    props.onClose()
                }
            )
        } else {
            // target === dataset
            datasetsBySchema(props.uri, pagination).then(
                response => {
                    setDatasets(response.datasets)
                    setCount(response.total)
                },
                error => {
                    dispatch(alertAction("FETCH_DATASETS_BY_URI", "Failed to fetch datasets by Schema URI.", error))
                    props.onClose()
                }
            )
        }
    }, [props.uri, props.jsonpath, dispatch, props.onClose])
    const executeQuery = (id: number) => {
        if (!props.jsonpath) {
            return ""
        }
        let response: Promise<QueryResult>
        if (props.target === "run") {
            response = query(id, props.jsonpath, false, props.uri)
        } else {
            response = queryDataset(id, props.jsonpath, false, props.uri)
        }
        return response.then(
            result => {
                setValid(result.valid)
                if (result.valid) {
                    try {
                        result.value = JSON.parse(result.value)
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
            <Flex>
                <FlexItem>
                    <JsonPathDocsLink />
                </FlexItem>
                <FlexItem grow={{ default: "grow" }}>
                    <TextInput
                        id="jsonpath"
                        value={props.jsonpath || ""}
                        onChange={value => {
                            setValid(true)
                            setResult(undefined)
                            props.onChange(value)
                        }}
                        validated={valid ? "default" : "error"}
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
                                                    )}`}
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
                            to={`/run/${target?.id}?query=${encodeURIComponent(props.jsonpath || "")}`}
                        >
                            Go to run {target?.id}
                        </NavLink>
                    )}
                    {props.target === "run" && (
                        <NavLink
                            className="pf-c-button pf-m-secondary"
                            to={`/run/${(target as Dataset).runId}?query=${encodeURIComponent(
                                props.jsonpath || ""
                            )}#dataset${(target as Dataset).ordinal}`}
                        >
                            Go to dataset {(target as Dataset).runId} #{(target as Dataset).ordinal + 1}
                        </NavLink>
                    )}
                </div>
            )}
        </Modal>
    )
}
