import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import { useHistory } from "react-router-dom"
import { Bullseye, Button, Form, FormGroup, Spinner } from "@patternfly/react-core"

import { dispatchError } from "../../alerts"
import { interleave, noop } from "../../utils"
import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"

import * as api from "./api"
import { getIdByUri } from "../schemas/api"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"

type DatasetDataProps = {
    runId: number
    datasetId: number
}

export default function DatasetData(props: DatasetDataProps) {
    const dispatch = useDispatch()
    const [originalData, setOriginalData] = useState<any>()
    const [editorData, setEditorData] = useState<string>()
    const [loading, setLoading] = useState(false)
    useEffect(() => {
        setLoading(true)
        api.getDataset(props.datasetId)
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
    const history = useHistory()
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
                                schemas.map((uri, i) => (
                                    <Button
                                        variant="link"
                                        key={2 * i}
                                        style={{ padding: 0, fontWeight: "var(--pf-global--link--FontWeight)" }}
                                        onClick={() =>
                                            getIdByUri(uri).then(
                                                id => history.push(`/schema/${id}`),
                                                error =>
                                                    dispatchError(
                                                        dispatch,
                                                        error,
                                                        "FIND_SCHEMA",
                                                        "Cannot find schema with URI " + uri
                                                    ).catch(noop)
                                            )
                                        }
                                    >
                                        {uri}
                                    </Button>
                                )),
                                i => <br key={2 * i + 1} />
                            )) ||
                            "(no schema)"}
                    </div>
                </FormGroup>
            </Form>
            <JsonPathSearchToolbar
                originalData={originalData}
                onRemoteQuery={(query, array) => api.queryDataset(props.datasetId, query, array)}
                onDataUpdate={setEditorData}
            />
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
