import { useEffect, useMemo, useState } from "react"
import { useSelector, useDispatch } from "react-redux"

import * as actions from "./actions"
import * as api from "./api"
import { Run, RunsDispatch } from "./reducers"
import { noop } from "../../utils"
import { useTester, teamsSelector } from "../../auth"
import { interleave } from "../../utils"
import { dispatchError } from "../../alerts"

import Editor from "../../components/Editor/monaco/Editor"

import { Bullseye, Button, Form, FormGroup, Spinner } from "@patternfly/react-core"
import { EditIcon } from "@patternfly/react-icons"
import { toString } from "../../components/Editor"
import { NavLink } from "react-router-dom"
import ChangeSchemaModal from "./ChangeSchemaModal"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInRun } from "./NoSchema"

function findFirstValue(o: any) {
    if (!o || Object.keys(o).length !== 1) {
        return undefined
    }
    const key = Object.keys(o)[0]
    return { id: parseInt(key), schema: o[key] }
}

function getPaths(data: any) {
    if (!data) {
        return []
    }
    return Object.keys(data).filter(k => typeof data[k] === "object")
}

type RunDataProps = {
    run: Run
}

export default function RunData(props: RunDataProps) {
    const [loading, setLoading] = useState(false)
    const [data, setData] = useState()
    const [editorData, setEditorData] = useState<string>()

    const [changeSchemaModalOpen, setChangeSchemaModalOpen] = useState(false)
    const dispatch = useDispatch<RunsDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        setLoading(true)
        api.getData(props.run.id, token || undefined)
            .then(
                data => {
                    setData(data)
                    setEditorData(toString(data))
                },
                error => dispatchError(dispatch, error, "FETCH_RUN_DATA", "Failed to fetch run data").catch(noop)
            )
            .finally(() => setLoading(false))
    }, [dispatch, props.run.id, teams])

    const isTester = useTester(props.run.owner)
    const memoizedEditor = useMemo(() => {
        // TODO: height 100% doesn't work
        return (
            <Editor
                height="600px"
                value={editorData}
                options={{
                    mode: "application/ld+json",
                    readOnly: true,
                }}
            />
        )
    }, [editorData])
    const schemas = props.run.schema
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
                            Object.keys(schemas).length > 0 &&
                            interleave(
                                Object.keys(schemas).map((key, i) => (
                                    <NavLink key={2 * i} to={`/schema/${key}`}>
                                        {schemas[key]}
                                    </NavLink>
                                )),
                                i => <br key={2 * i + 1} />
                            )) || <NoSchemaInRun />}
                        {isTester && (
                            <>
                                <Button
                                    variant="link"
                                    style={{ paddingTop: 0 }}
                                    onClick={() => setChangeSchemaModalOpen(true)}
                                >
                                    <EditIcon />
                                </Button>
                                <ChangeSchemaModal
                                    isOpen={changeSchemaModalOpen}
                                    onClose={() => setChangeSchemaModalOpen(false)}
                                    initialSchema={findFirstValue(schemas)}
                                    paths={getPaths(data)}
                                    update={(path, schema, schemaid) =>
                                        dispatch(
                                            actions.updateSchema(props.run.id, props.run.testid, path, schemaid, schema)
                                        ).catch(noop)
                                    }
                                />
                            </>
                        )}
                    </div>
                </FormGroup>
            </Form>
            <JsonPathSearchToolbar
                originalData={data}
                onRemoteQuery={(query, array) => api.query(props.run.id, query, array)}
                onDataUpdate={setEditorData}
            />
            {loading ? (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            ) : (
                memoizedEditor
            )}
        </>
    )
}
