import {useContext, useEffect, useState} from "react"
import { Button, Flex, FlexItem } from "@patternfly/react-core"

import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import MaybeLoading from "../../components/MaybeLoading"
import DatasetLogModal from "../tests/DatasetLogModal"

import {datasetApi, experimentApi, SchemaUsage, sqlApi, ValidationError} from "../../api"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInDataset } from "./NoSchema"
import LabelValuesModal from "./LabelValuesModal"
import ExperimentModal from "./ExperimentModal"
import SchemaValidations from "./SchemaValidations"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type DatasetDataProps = {
    testId: number
    runId: number
    datasetId: number
}

export default function DatasetData(props: DatasetDataProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [originalData, setOriginalData] = useState<any>()
    const [editorData, setEditorData] = useState<string>()
    const [validationErrors, setValidationErrors] = useState<ValidationError[]>([])
    const [schemas, setSchemas] = useState<SchemaUsage[]>()
    const [labelValuesOpen, setLabelValuesOpen] = useState(false)
    const [labelsLogOpen, setLabelsLogOpen] = useState(false)
    const [hasExperiments, setHasExperiments] = useState(false)
    const [experimentsOpen, setExperimentsOpen] = useState(false)
    useEffect(() => {
        datasetApi.getDataset(props.datasetId)
            .then(
                dataset => {
                    setOriginalData(dataset.data)
                    setEditorData(toString(dataset.data))
                    setValidationErrors(dataset.validationErrors || [])
                },
                error =>
                    alerting.dispatchError( error, "FETCH_DATASET", "Failed to fetch dataset " + props.datasetId)
            )

    }, [props.datasetId])
    useEffect(() => {
        datasetApi.getSummary(props.datasetId).then(
            ds => setSchemas(ds.schemas),
            e => alerting.dispatchError( e, "FETCH_DATASET_SUMMARY", "Failed to fetch dataset schemas")
        )
    }, [props.datasetId])
    useEffect(() => {
        experimentApi.profiles(props.testId).then(
            profiles => setHasExperiments(profiles && profiles.length > 0),
            error => alerting.dispatchError( error, "FETCH_EXPERIMENT_PROFILES", "Cannot fetch experiment profiles")
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
                        onRemoteQuery={(query, array) => sqlApi.queryDatasetData(props.datasetId, query, array)}
                        onDataUpdate={setEditorData}
                    />
                </FlexItem>
                <FlexItem style={{ marginLeft: "auto", marginRight: "0px" }}>
                    <Button variant="primary" style={{ marginRight: "16px" }} onClick={() => setLabelValuesOpen(true)}>
                        Show label values
                    </Button>
                    <LabelValuesModal
                        datasetId={props.datasetId}
                        isOpen={labelValuesOpen}
                        onClose={() => setLabelValuesOpen(false)}
                    />
                    <Button variant="secondary" style={{ marginRight: "16px" }} onClick={() => setLabelsLogOpen(true)}>
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
                            <Button variant="primary" style={{ marginRight: "16px" }} onClick={() => setExperimentsOpen(true)}>
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
                <Editor
                    height="600px"
                    value={editorData}
                    options={{
                        mode: "application/ld+json",
                        readOnly: true,
                    }}
                />
        </>
    )
}
