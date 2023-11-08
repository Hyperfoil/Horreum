import {useContext, useEffect, useMemo, useState} from "react"

import { Bullseye, Spinner } from "@patternfly/react-core"

import {runApi, RunExtended} from "../../api"
import Editor from "../../components/Editor/monaco/Editor"
import { toString } from "../../components/Editor"
import { NoSchemaInRun } from "./NoSchema"
import SchemaValidations from "./SchemaValidations"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type MetaDataProps = {
    run: RunExtended
}

export default function MetaData(props: MetaDataProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [loading, setLoading] = useState(false)
    const [metadata, setMetadata] = useState<string>()
    useEffect(() => {
        setLoading(true)
        runApi.getMetadata(props.run.id)
            .then(
                md => setMetadata(toString(md)),
                error => alerting.dispatchError( error, "FETCH_METADATA", "Failed to fetch run metadata")
            )
            .finally(() => setLoading(false))
    }, [props.run.id])
    const memoizedEditor = useMemo(() => {
        // TODO: height 100% doesn't work
        return (
            <Editor
                height="600px"
                value={metadata}
                options={{
                    mode: "application/ld+json",
                    readOnly: true,
                }}
            />
        )
    }, [metadata])
    if (loading) {
        return (
            <Bullseye>
                <Spinner size="xl" />
            </Bullseye>
        )
    }
    return (
        <>
            <SchemaValidations
                schemas={props.run.schemas.filter(s => s.source === 1)}
                errors={props.run.validationErrors || []}
                noSchema={<NoSchemaInRun />}
            />
            <br />
            {memoizedEditor}
        </>
    )
}
