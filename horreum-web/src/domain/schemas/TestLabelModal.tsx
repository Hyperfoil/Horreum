import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import { NavLink } from "react-router-dom"

import { Bullseye, Button, Modal, Pagination, Spinner, Title } from "@patternfly/react-core"
import { Table, TableBody, TableHeader } from "@patternfly/react-table"

import Editor from "../../components/Editor/monaco/Editor"
import { toString } from "../../components/Editor"
import {datasetApi, DatasetSummary, Label, SortDirection} from "../../api"
import { alertAction } from "../../alerts"

type TestLabelModalProps = {
    isOpen: boolean
    onClose(): void
    uri: string
    label: Label
}

export default function TestLabelModal(props: TestLabelModalProps) {
    const [datasets, setDatasets] = useState<DatasetSummary[]>()
    const [count, setCount] = useState(0)
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [loading, setLoading] = useState(false)
    const [result, setResult] = useState<any>()
    const [output, setOutput] = useState<string>()
    const [hasResult, setHasResult] = useState(false)
    const pagination = useMemo(() => ({ page, perPage, sort: "start", direction: "Descending" }), [page, perPage])
    const dispatch = useDispatch()
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        setLoading(true)
        datasetApi.listBySchema(
            props.uri,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending,
            pagination.perPage,
            pagination.page,
            pagination.sort
        )
            .then(
                summary => {
                    setDatasets(summary.datasets)
                    setCount(summary.total)
                },
                error => {
                    dispatch(alertAction("FETCH_DATASETS_BY_URI", "Failed to fetch datasets by Schema URI.", error))
                    props.onClose()
                }
            )
            .finally(() => setLoading(false))
    }, [props.uri, pagination, dispatch, props.onClose])
    function reset() {
        setDatasets(undefined)
        setCount(0)
        setPage(1)
        setPerPage(20)
        setResult(undefined)
        setHasResult(false)
        setOutput(undefined)
    }
    return (
        <Modal
            variant="large"
            isOpen={props.isOpen}
            onClose={() => {
                reset()
                props.onClose()
            }}
            title="Test label calculation"
        >
            {loading && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {datasets && !hasResult && (
                <>
                    <Table
                        aria-label="Available datasets"
                        variant="compact"
                        cells={["Test", "Dataset", "Description", ""]}
                        rows={datasets.map(d => ({
                            cells: [
                                d.testname,
                                {
                                    title: (
                                        <NavLink to={`/run/${d.runId}#dataset${d.ordinal}`}>
                                            {d.runId}/{d.ordinal + 1}
                                        </NavLink>
                                    ),
                                },
                                d.description,
                                {
                                    title: (
                                        <Button
                                            onClick={() => {
                                                setLoading(true)
                                                datasetApi.previewLabel(d.id, props.label)
                                                    .then(
                                                        preview => {
                                                            setResult(preview.value)
                                                            setHasResult(true)
                                                            setOutput(preview.output)
                                                        },
                                                        error => {
                                                            dispatch(
                                                                alertAction(
                                                                    "LABEL_PREVIEW",
                                                                    "Failed to fetch label preview",
                                                                    error
                                                                )
                                                            )
                                                            reset()
                                                            props.onClose()
                                                        }
                                                    )
                                                    .finally(() => setLoading(false))
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
                    <Pagination
                        itemCount={count}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </>
            )}
            {hasResult && (
                <>
                    <Title headingLevel="h4">Result:</Title>
                    <div style={{ minHeight: "100px", height: "250px", resize: "vertical", overflow: "auto" }}>
                        <Editor value={result === undefined ? "null" : toString(result)} options={{ readOnly: true }} />
                    </div>
                </>
            )}
            {output !== undefined && (
                <>
                    <Title headingLevel="h4">Debug output:</Title>
                    <div style={{ minHeight: "100px", height: "250px", resize: "vertical", overflow: "auto" }}>
                        <Editor value={output} options={{ readOnly: true }} />
                    </div>
                </>
            )}
        </Modal>
    )
}
