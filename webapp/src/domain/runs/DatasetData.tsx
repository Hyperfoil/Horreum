import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import { Button, Bullseye, Flex, FlexItem, Form, FormGroup, Spinner } from "@patternfly/react-core"

import { dispatchError } from "../../alerts"
import { interleave, noop } from "../../utils"
import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import SchemaLink from "../schemas/SchemaLink"
import DatasetLogModal from "../tests/DatasetLogModal"

import Api from "../../api"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInDataset } from "./NoSchema"
import LabelValuesModal from "./LabelValuesModal"

type DatasetDataProps = {
    testId: number
    runId: number
    datasetId: number
}

export default function DatasetData(props: DatasetDataProps) {
    const dispatch = useDispatch()
    const [originalData, setOriginalData] = useState<any>()
    const [editorData, setEditorData] = useState<string>()
    const [loading, setLoading] = useState(false)
    const [labelValuesOpen, setLabelValuesOpen] = useState(false)
    const [labelsLogOpen, setLabelsLogOpen] = useState(false)
    useEffect(() => {
        setLoading(true)
        Api.datasetServiceGetDataSet(props.datasetId)
            .then(
                dataset => {
                    setOriginalData(dataset.data)
                    setEditorData(toString(dataset.data))
                },
                error =>
                    dispatchError(dispatch, error, "FETCH_DATASET", "Failed to fetch dataset " + props.datasetId).catch(
                        noop
                    )
            )
            .finally(() => setLoading(false))
    }, [props.datasetId])
    const schemas = useMemo(() => {
        if (originalData) {
            return [
                originalData["$schema"],
                ...Object.values(originalData).map(v => (typeof v === "object" ? (v as any)["$schema"] : undefined)),
            ].filter(uri => !!uri)
        } else {
            return []
        }
    }, [originalData])
    return (
        <>
            <Form isHorizontal>
                <FormGroup label="Schemas" fieldId="schemas">
                    <div
                        style={{
                            display: "flex",
                            paddingTop: "var(--pf-c-form--m-horizontal__group-label--md--PaddingTop)",
                        }}
                    >
                        {(schemas &&
                            schemas.length > 0 &&
                            interleave(
                                schemas.map((uri, i) => <SchemaLink uri={uri} key={2 * i} />),
                                i => <br key={2 * i + 1} />
                            )) || <NoSchemaInDataset />}
                    </div>
                </FormGroup>
            </Form>
            <Flex alignItems={{ default: "alignItemsCenter" }}>
                <FlexItem>
                    <JsonPathSearchToolbar
                        originalData={originalData}
                        onRemoteQuery={(query, array) => Api.datasetServiceQueryData(props.datasetId, query, array)}
                        onDataUpdate={setEditorData}
                    />
                </FlexItem>
                <FlexItem>
                    <Button variant="primary" onClick={() => setLabelValuesOpen(true)}>
                        Show label values
                    </Button>
                    <LabelValuesModal
                        datasetId={props.datasetId}
                        isOpen={labelValuesOpen}
                        onClose={() => setLabelValuesOpen(false)}
                    />
                    <Button variant="secondary" onClick={() => setLabelsLogOpen(true)}>
                        Labels log
                    </Button>
                    <DatasetLogModal
                        testId={props.testId}
                        datasetId={props.datasetId}
                        source="labels"
                        title="Labels calculation log"
                        emptyMessage="There are no logs."
                        isOpen={labelsLogOpen}
                        onClose={() => setLabelsLogOpen(false)}
                    />
                </FlexItem>
            </Flex>
            {loading ? (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            ) : (
                <Editor
                    height="600px"
                    value={editorData}
                    options={{
                        mode: "application/ld+json",
                        readOnly: true,
                    }}
                />
            )}
        </>
    )
}
