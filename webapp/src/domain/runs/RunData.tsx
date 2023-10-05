import { useEffect, useMemo, useState } from "react"
import { useSelector, useDispatch } from "react-redux"

import * as actions from "./actions"
import { RunsDispatch } from "./reducers"
import { noop } from "../../utils"
import { useTester, teamsSelector } from "../../auth"
import { dispatchError } from "../../alerts"

import Editor from "../../components/Editor/monaco/Editor"

import Api, { RunExtended } from "../../api"
import { toString } from "../../components/Editor"
import ChangeSchemaModal from "./ChangeSchemaModal"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInRun } from "./NoSchema"
import SchemaValidations from "./SchemaValidations"

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
    return Object.keys(data).filter(k => {
        const value = data[k]
        return typeof value === "object" && !Array.isArray(value) && value
    })
}

type RunDataProps = {
    run: RunExtended
    onUpdate(): void
}

export default function RunData(props: RunDataProps) {
    const [data, setData] = useState()
    const [editorData, setEditorData] = useState<string>()
    const [updateCounter, setUpdateCounter] = useState(0)

    const [changeSchemaModalOpen, setChangeSchemaModalOpen] = useState(false)
    const dispatch = useDispatch<RunsDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        Api.runServiceGetData(props.run.id, token || undefined)
            .then(
                data => {
                    setData(data as any)
                    setEditorData(toString(data))
                },
                error => dispatchError(dispatch, error, "FETCH_RUN_DATA", "Failed to fetch run data").catch(noop)
            )
    }, [dispatch, props.run.id, teams, updateCounter])

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
    const schemas = props.run.schemas.filter(s => s.source == 0)
    return (
        <>
            <SchemaValidations
                schemas={props.run.schemas.filter(s => s.source === 0)}
                errors={props.run.validationErrors || []}
                onEdit={isTester ? () => setChangeSchemaModalOpen(true) : undefined}
                noSchema={<NoSchemaInRun />}
            />
            {isTester && (
                <ChangeSchemaModal
                    isOpen={changeSchemaModalOpen}
                    onClose={() => setChangeSchemaModalOpen(false)}
                    initialSchema={findFirstValue(schemas)}
                    paths={getPaths(data)}
                    hasRoot={typeof data === "object" && !Array.isArray(data) && data}
                    update={(path, schemaUri, _) =>
                        dispatch(actions.updateSchema(props.run.id, props.run.testid, path, schemaUri))
                            .catch(noop)
                            .then(() => {
                                props.onUpdate()
                                setUpdateCounter(updateCounter + 1)
                            })
                    }
                />
            )}
            <JsonPathSearchToolbar
                originalData={data}
                onRemoteQuery={(query, array) => Api.sqlServiceQueryRunData(props.run.id, query, array)}
                onDataUpdate={setEditorData}
            />
            {memoizedEditor}
        </>
    )
}
