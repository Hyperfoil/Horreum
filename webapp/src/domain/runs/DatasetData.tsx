import React, { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import { Button, Bullseye, Flex, FlexItem, Form, FormGroup, Spinner } from "@patternfly/react-core"

import { dispatchError } from "../../alerts"
import { interleave, noop } from "../../utils"
import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import SchemaLink from "../schemas/SchemaLink"
import DatasetLogModal from "../tests/DatasetLogModal"

import Api, { ValidationError } from "../../api"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInDataset } from "./NoSchema"
import LabelValuesModal from "./LabelValuesModal"
import ValidationErrorTable from "./ValidationErrorTable"
import ExperimentModal from "./ExperimentModal"
import ErrorBadge from "../../components/ErrorBadge"

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
    const [erroredSchemas, setErroredSchemas] = useState<Record<number, string>>({})
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
        if (validationErrors.length > 0) {
            Api.schemaServiceDescriptors(validationErrors.map(e => e.schemaId)).then(
                ds =>
                    setErroredSchemas(
                        ds.reduce(
                            (acc, d) => ({
                                ...acc,
                                [d.id]: d.uri,
                            }),
                            {}
                        )
                    ),
                e => dispatchError(dispatch, e, "FETCH_SCHEMA_URIS", "Failed to fetch schema URIs").catch(noop)
            )
        }
    }, [validationErrors])
    useEffect(() => {
        Api.experimentServiceProfiles(props.testId).then(
            profiles => setHasExperiments(profiles && profiles.length > 0),
            error => dispatchError(dispatch, error, "FETCH_EXPERIMENT_PROFILES", "Cannot fetch experiment profiles")
        )
    }, [props.testId])
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
                            paddingTop: "var(--pf-c-form--m-horizontal__group-label--md--PaddingTop)",
                        }}
                    >
                        {(schemas &&
                            schemas.length > 0 &&
                            interleave(
                                schemas.map((uri, i) => {
                                    const pair = Object.entries(erroredSchemas).find(([_, eu]) => eu === uri)
                                    const schemaId = pair && parseInt(pair[0])
                                    const errors = schemaId ? validationErrors.filter(e => e.schemaId === schemaId) : []
                                    return (
                                        <React.Fragment key={2 * i}>
                                            <SchemaLink uri={uri} />
                                            {errors.length > 0 && <ErrorBadge>{errors.length}</ErrorBadge>}
                                        </React.Fragment>
                                    )
                                }),
                                i => <br key={2 * i + 1} />
                            )) || <NoSchemaInDataset />}
                    </div>
                </FormGroup>
                {validationErrors.length > 0 && (
                    <FormGroup label="Validation errors" fieldId="none">
                        <ValidationErrorTable errors={validationErrors} uris={erroredSchemas} />
                    </FormGroup>
                )}
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
