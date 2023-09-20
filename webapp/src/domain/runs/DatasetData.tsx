import { useEffect, useState } from "react"
import { useDispatch } from "react-redux"
import { Button, Flex, FlexItem } from "@patternfly/react-core"

import { dispatchError } from "../../alerts"
import { noop } from "../../utils"
import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import MaybeLoading from "../../components/MaybeLoading"
import DatasetLogModal from "../tests/DatasetLogModal"

import Api, { SchemaUsage, ValidationError } from "../../api"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInDataset } from "./NoSchema"
import LabelValuesModal from "./LabelValuesModal"
import ExperimentModal from "./ExperimentModal"
import SchemaValidations from "./SchemaValidations"

type DatasetDataProps = {
    testId: number
    runId: number
    datasetId: number
}

export default function DatasetData(props: DatasetDataProps) {
    const dispatch = useDispatch()
    const [originalData, setOriginalData] = useState<any>()
    const [editorData, setEditorData] = useState<string>()
    const [validationErrors, setValidationErrors] = useState<ValidationError[]>([])
    const [schemas, setSchemas] = useState<SchemaUsage[]>()
    const [loading, setLoading] = useState(false)
    const [labelValuesOpen, setLabelValuesOpen] = useState(false)
    const [labelsLogOpen, setLabelsLogOpen] = useState(false)
    const [hasExperiments, setHasExperiments] = useState(false)
    const [experimentsOpen, setExperimentsOpen] = useState(false)
    useEffect(() => {
        setLoading(true)
        Api.datasetServiceGetDataSet(props.datasetId)
            .then(
                dataset => {
                    setOriginalData(dataset.data)
                    setEditorData(toString(dataset.data))
                    setValidationErrors(dataset.validationErrors || [])
                },
                error =>
                    dispatchError(dispatch, error, "FETCH_DATASET", "Failed to fetch dataset " + props.datasetId).catch(
                        noop
                    )
            )
            .finally(() => setLoading(false))
    }, [props.datasetId])
    useEffect(() => {
        Api.datasetServiceGetSummary(props.datasetId).then(
            ds => setSchemas(ds.schemas),
            e => dispatchError(dispatch, e, "FETCH_DATASET_SUMMARY", "Failed to fetch dataset schemas").catch(noop)
        )
    }, [props.datasetId])
    useEffect(() => {
        Api.experimentServiceProfiles(props.testId).then(
            profiles => setHasExperiments(profiles && profiles.length > 0),
            error => dispatchError(dispatch, error, "FETCH_EXPERIMENT_PROFILES", "Cannot fetch experiment profiles")
        )
    }, [props.testId])

    return (
        <>
            <MaybeLoading loading={!schemas}>
                {schemas && (
                    <SchemaValidations schemas={schemas} errors={validationErrors} noSchema={<NoSchemaInDataset />} />
                )}
            </MaybeLoading>
            <Flex alignItems={{ default: "alignItemsCenter" }}>
                <FlexItem>
                    <JsonPathSearchToolbar
                        originalData={originalData}
                        onRemoteQuery={(query, array) => Api.sqlServiceQueryDatasetData(props.datasetId, query, array)}
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
                    {hasExperiments && (
                        <>
                            <Button variant="primary" onClick={() => setExperimentsOpen(true)}>
                                Evaluate experiment
                            </Button>
                            <ExperimentModal
                                datasetId={props.datasetId}
                                isOpen={experimentsOpen}
                                onClose={() => setExperimentsOpen(false)}
                            />
                        </>
                    )}
                </FlexItem>
            </Flex>
            <MaybeLoading loading={loading}>
                <Editor
                    height="600px"
                    value={editorData}
                    options={{
                        mode: "application/ld+json",
                        readOnly: true,
                    }}
                />
            </MaybeLoading>
        </>
    )
}
