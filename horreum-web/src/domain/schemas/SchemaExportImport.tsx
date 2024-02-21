import { useState, useRef } from "react"

import { Button, Modal } from "@patternfly/react-core"

import {schemaApi, SchemaExport} from "../../api"
import ExportImport from "../../components/ExportImport"

type SchemaExportImportProps = {
    id: number
    name: string
}

export default function SchemaExportImport(props: SchemaExportImportProps) {
    const [importConfig, setImportConfig] = useState<any>(undefined)
    const importResolve = useRef<(v: any) => void>()
    const resolve = (valid: boolean) => {
        if (importResolve.current) {
            importResolve.current(valid)
        }
        setImportConfig(false)
        importResolve.current = undefined
    }
    const isValid = importConfig?.id !== undefined && importConfig?.name !== undefined
    return (
        <>
            <ExportImport
                name={props.name}
                export={() => schemaApi.exportSchema(props.id)}
                import={cfg => schemaApi.importSchema(cfg as SchemaExport)}
                validate={cfg => {
                    return new Promise((resolve, _) => {
                        setImportConfig(cfg)
                        importResolve.current = resolve
                    })
                }}
            />
            {importConfig && (
                <Modal
                    variant="small"
                    isOpen={true}
                    title="Import schema?"
                    showClose={false}
                    actions={[
                        isValid ? (
                            <Button variant="danger" onClick={() => resolve(true)}>
                                Continue importing
                            </Button>
                        ) : null,
                        <Button variant="secondary" onClick={() => resolve(false)}>
                            Cancel
                        </Button>,
                    ]}
                >
                    {!isValid ? (
                        <>
                            The imported object does not have ID or name; most likely this is not a valid schema
                            configuration.
                        </>
                    ) : importConfig.id !== props.id ? (
                        <>
                            The imported configuration <b>does not match</b> to current schema; you are importing schema{" "}
                            {importConfig.name || "<unknown name>"} with ID {importConfig.id || "<unknown ID>"}. This
                            will either create a new schema or overwrite an existing one.
                            <br />
                        </>
                    ) : (
                        <>
                            This import will overwrite <b>all</b> configuration for schema {props.name}
                            {importConfig.name !== props.name && `(overwriting name to ${importConfig.name})`}
                        </>
                    )}
                    {isValid && (
                        <>
                            <br />
                            <br />
                            Do you really want to proceed?
                        </>
                    )}
                </Modal>
            )}
        </>
    )
}
