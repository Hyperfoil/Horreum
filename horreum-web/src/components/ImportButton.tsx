import {useContext, useState} from "react"

import { Bullseye, Button, FileUpload, Modal, Spinner } from "@patternfly/react-core"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";

type ImportProps = {
    label?: string
    onLoad(config: Record<string, unknown>): Promise<any> | any
    onImport(config: string): Promise<unknown>
    onImported(): void
}

export default function ImportButton({label, onLoad, onImport, onImported}: ImportProps) {
    const { alerting } = useContext(AppContext) as AppContextType;

    const [open, setOpen] = useState(false)
    const [filename, setFilename] = useState<string>()
    const [loading, setLoading] = useState(false)
    const [checking, setChecking] = useState(false)
    const [config, setConfig] = useState<string>()
    const [uploading, setUploading] = useState(false)
    const [parseError, setParseError] = useState<any>()
    const [overridden, setOverridden] = useState<any>()
    const clear = () => {
        setFilename(undefined)
        setConfig(undefined)
        setUploading(false)
        setParseError(undefined)
        setOverridden(undefined)
    }
    const close = () => {
        setOpen(false)
        clear()
    }
    return (
        <>
            <Button variant="secondary" onClick={() => setOpen(true)}>
                {label || "Import"}
            </Button>
            <Modal
                title={label}
                variant="small"
                isOpen={open}
                onClose={close}
                actions={[
                    <Button
                        key="import"
                        variant={overridden && "danger" || "primary"}
                        isDisabled={!config || uploading}
                        onClick={() => {
                            setUploading(true)
                            if (!config) {
                                return
                            }
                                onImport(config)
                                .then(
                                    () => {
                                        alerting.dispatchInfo( "IMPORT", "Import succeeded", "", 3000)
                                        onImported()
                                    },
                                    error => alerting.dispatchError( error, "IMPORT_FAILED", "Import failed")
                                )
                                .finally(close)
                        }}
                    >
                        Import
                    </Button>,
                    <Button variant="secondary" onClick={close} key="cancel">
                        Cancel
                    </Button>,
                ]}
            >
                {uploading ? (
                    <Bullseye>
                        <Spinner size="xl" />
                    </Bullseye>
                ) : (
                    <FileUpload
                        id="import"
                        isLoading={loading}
                        filename={filename}
                        onClearClick={clear}
                        onFileInputChange={(_, file) => {
                            setFilename(file.name)
                            setLoading(true)
                            setParseError(undefined)
                            setOverridden(undefined)
                            file.text()
                                .then(cfg => {
                                    try {
                                        const parsed = JSON.parse(cfg || "")
                                        setConfig(parsed)
                                        setChecking(true)
                                        Promise.resolve(onLoad(parsed))
                                            .then(value => setOverridden(value))
                                            .finally(() => setChecking(false))
                                    } catch (e) {
                                        setParseError(e)
                                    }
                                })
                                .finally(() => setLoading(false))
                        }}
                    />
                )}
                {parseError !== undefined && parseError.toString() || null}
                {overridden ||
                    (checking && (
                        <Bullseye>
                            <Spinner size="xl" />
                        </Bullseye>
                    ))}
            </Modal>
        </>
    )
}
