import { useState } from "react"
import { useDispatch } from "react-redux"

import { dispatchError, dispatchInfo } from "../alerts"

import { Bullseye, Button, FileUpload, Modal, Spinner } from "@patternfly/react-core"

type ImportProps = {
    label?: string
    onLoad(config: Record<string, unknown>): any
    onImport(config: Record<string, unknown>): Promise<unknown>
    onImported(): void
}

export default function ImportButton(props: ImportProps) {
    const [open, setOpen] = useState(false)
    const [filename, setFilename] = useState<string>()
    const [loading, setLoading] = useState(false)
    const [config, setConfig] = useState<Record<string, unknown>>()
    const [uploading, setUploading] = useState(false)
    const [parseError, setParseError] = useState<any>()
    const [overridden, setOverridden] = useState<any>()
    const dispatch = useDispatch()
    const clear = () => {
        setFilename(undefined)
        setConfig(undefined)
        setUploading(false)
        setParseError(undefined)
    }
    const close = () => {
        setOpen(false)
        clear()
    }
    return (
        <>
            <Button variant="secondary" onClick={() => setOpen(true)}>
                {props.label || "Import"}
            </Button>
            <Modal
                title="Import test"
                variant="small"
                isOpen={open}
                onClose={close}
                actions={[
                    <Button
                        key="import"
                        variant={overridden ? "danger" : "primary"}
                        isDisabled={!config || uploading}
                        onClick={() => {
                            setUploading(true)
                            if (!config) {
                                return
                            }
                            props
                                .onImport(config)
                                .then(
                                    () => {
                                        dispatchInfo(dispatch, "IMPORT", "Import succeeded", "", 3000)
                                        props.onImported()
                                    },
                                    error => dispatchError(dispatch, error, "IMPORT_FAILED", "Import failed")
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
                            file.text()
                                .then(cfg => {
                                    try {
                                        const parsed = JSON.parse(cfg || "")
                                        setConfig(parsed)
                                        setOverridden(props.onLoad(parsed))
                                    } catch (e) {
                                        setParseError(e)
                                    }
                                })
                                .finally(() => setLoading(false))
                        }}
                    />
                )}
                {parseError !== undefined ? parseError.toString() : null}
                {overridden}
            </Modal>
        </>
    )
}
