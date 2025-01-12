import {useContext, useEffect, useMemo, useState} from "react"
import { NavLink } from "react-router-dom"

import {Bullseye, Button, Pagination, Spinner, Title} from '@patternfly/react-core';
import {Modal} from '@patternfly/react-core/deprecated';

import Editor from "../../components/Editor/monaco/Editor"
import { toString } from "../../components/Editor"
import {datasetApi, DatasetSummary, Label, SortDirection} from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {OuterScrollContainer, Table, Tbody, Td, Th, Thead, Tr} from "@patternfly/react-table";

type TestLabelModalProps = {
    isOpen: boolean
    onClose(): void
    uri: string
    label: Label
}

export default function TestLabelModal(props: TestLabelModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [datasets, setDatasets] = useState<DatasetSummary[]>()
    const [count, setCount] = useState(0)
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [loading, setLoading] = useState(false)
    const [result, setResult] = useState<any>()
    const [output, setOutput] = useState<string>()
    const [hasResult, setHasResult] = useState(false)
    const pagination = useMemo(() => ({ page, perPage, sort: "start", direction: "Descending" }), [page, perPage])
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        setLoading(true)
        datasetApi.listDatasetsBySchema(
            props.uri,
            pagination.perPage,
            pagination.page,
            pagination.sort,
            pagination.direction === "Descending" ? SortDirection.Descending : SortDirection.Ascending
        )
            .then(
                summary => {
                    setDatasets(summary.datasets)
                    setCount(summary.total)
                },
                error => {
                    alerting.dispatchError(error,"FETCH_DATASETS_BY_URI", "Failed to fetch datasets by Schema URI.")
                    props.onClose()
                }
            )
            .finally(() => setLoading(false))
    }, [props.uri, pagination, props.onClose])
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
                    <OuterScrollContainer style={{ overflowY: "auto", height: "80vh" }}>
                        <Table aria-label="Available datasets" variant="compact" isStickyHeader>
                            <Thead>
                                <Tr>
                                    {["Test", "Dataset", "Description", ""].map((col, index) =>
                                        <Th key={index} aria-label={"header-" + index}>{col}</Th>
                                    )}
                                </Tr>
                            </Thead>
                            <Tbody>
                                {datasets.map((dataset, index) =>
                                    <Tr key={index}>
                                        <Td key="Test">{dataset.testname}</Td>
                                        <Td key="Dataset">
                                            <NavLink to={`/run/${dataset.runId}#dataset${dataset.ordinal}`}>{dataset.runId}/{dataset.ordinal}</NavLink>
                                        </Td>
                                        <Td key="Description">{dataset.description}</Td>
                                        <Td key="">
                                            <Button
                                                size="sm"
                                                onClick={() => {
                                                    setLoading(true)
                                                    datasetApi.previewLabel(dataset.id, props.label)
                                                        .then(
                                                            preview => {
                                                                setResult(preview.value)
                                                                setHasResult(true)
                                                                setOutput(preview.output)
                                                            },
                                                            error => {
                                                                alerting.dispatchError(
                                                                    error,
                                                                    "LABEL_PREVIEW",
                                                                    "Failed to fetch label preview"
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
                                        </Td>
                                    </Tr>
                                )}
                            </Tbody>
                        </Table>
                    </OuterScrollContainer>
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
