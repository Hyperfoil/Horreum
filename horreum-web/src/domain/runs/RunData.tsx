import {useContext, useEffect, useMemo, useState} from "react"
import { useSelector } from "react-redux"

import { useTester, teamsSelector } from "../../auth"

import Editor from "../../components/Editor/monaco/Editor"

import {runApi, RunExtended, sqlApi} from "../../api"
import { toString } from "../../components/Editor"
import ChangeSchemaModal from "./ChangeSchemaModal"
import JsonPathSearchToolbar from "./JsonPathSearchToolbar"
import { NoSchemaInRun } from "./NoSchema"
import SchemaValidations from "./SchemaValidations"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

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
    updateCounter: number
    onUpdate(): void
}

export default function RunData(props: RunDataProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [data, setData] = useState()
    const [editorData, setEditorData] = useState<string>()

    const [changeSchemaModalOpen, setChangeSchemaModalOpen] = useState(false)
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        runApi.getData(props.run.id, token || undefined)
            .then(
                data => {
                    setData(data as any)
                    setEditorData(toString(data))
                },
                error => alerting.dispatchError( error, "FETCH_RUN_DATA", "Failed to fetch run data")
            )
    }, [ props.run.id, teams, props.updateCounter])

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
                        runApi.updateSchema(props.run.id, schemaUri, path).then(
                            () => props.onUpdate(),
                            error => alerting.dispatchError(error, "SCHEME_UPDATE_FAILED", "Failed to update run schema")
                        )
                    }
                />
            )}
            <JsonPathSearchToolbar
                originalData={data}
                onRemoteQuery={(query, array) => sqlApi.queryRunData(props.run.id, query, array)}
                onDataUpdate={setEditorData}
            />
            {memoizedEditor}
        </>
    )
}
