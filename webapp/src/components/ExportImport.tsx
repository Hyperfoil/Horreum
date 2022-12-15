import { useState } from "react"
import { useDispatch } from "react-redux"

import { Button, FileUpload, Flex, FlexItem, Form, FormGroup, Spinner } from "@patternfly/react-core"

import { dispatchError, dispatchInfo } from "../alerts"

type ExportImportProps = {
    name: string
    export(): Promise<any>
    import(cfg: Record<string, unknown>): Promise<void>
    validate(cfg: Record<string, unknown>): Promise<boolean>
}

export default function ExportImport(props: ExportImportProps) {
    const [downloading, setDownloading] = useState(false)
    const [uploadName, setUploadName] = useState<string>()
    const [loading, setLoading] = useState(false)
    const [uploadContent, setUploadContent] = useState<Record<string, unknown>>()
    const [uploading, setUploading] = useState(false)
    const [parseError, setParseError] = useState<any>(undefined)
    const dispatch = useDispatch()
    return (
        <Form isHorizontal>
            <FormGroup label="Export" fieldId="export">
                <Button
                    id="export"
                    isDisabled={downloading}
                    onClick={() => {
                        setDownloading(true)
                        props
                            .export()
                            .then(
                                cfg => {
                                    const url = window.URL.createObjectURL(new Blob([cfg]))
                                    const link = document.createElement("a")
                                    link.href = url
                                    link.setAttribute("download", `${props.name}.json`)
                                    document.body.appendChild(link)
                                    link.click()
                                    if (link.parentNode) {
                                        link.parentNode.removeChild(link)
                                    }
                                },
                                error => dispatchError(dispatch, error, "EXPORT", "Cannot export configuration")
                            )
                            .finally(() => setDownloading(false))
                    }}
                >
                    Export
                    {downloading && (
                        <>
                            {" "}
                            <Spinner size="md" />
                        </>
                    )}
                </Button>
            </FormGroup>
            <FormGroup
                label="Import"
                fieldId="import"
                validated={parseError ? "error" : "default"}
                helperTextInvalid={parseError?.toString()}
            >
                <Flex>
                    <FlexItem>
                        <FileUpload
                            id="import"
                            filename={uploadName}
                            isLoading={loading}
                            onFileInputChange={(e, file) => {
                                setLoading(true)
                                setUploadName(file.name)
                                setUploadContent(undefined)
                                setParseError(false)
                                file.text()
                                    .then(
                                        cfg => {
                                            try {
                                                setUploadContent(JSON.parse(cfg) as Record<string, unknown>)
                                            } catch (e) {
                                                setParseError(e)
                                                return
                                            }
                                        },
                                        error => {
                                            dispatchError(dispatch, error, "LOAD_FILE", "Cannot load file for import")
                                            setUploadContent(undefined)
                                        }
                                    )
                                    .finally(() => setLoading(false))
                            }}
                            onClearClick={() => {
                                setUploadName(undefined)
                                setUploadContent(undefined)
                            }}
                        />
                    </FlexItem>
                    <FlexItem>
                        <Button
                            isDisabled={!uploadContent || uploading}
                            onClick={() => {
                                if (!uploadContent) {
                                    return
                                }
                                props.validate(uploadContent).then(
                                    valid => {
                                        if (valid) {
                                            setUploading(true)
                                            props
                                                .import(uploadContent)
                                                .then(() =>
                                                    dispatchInfo(dispatch, "IMPORT", "Import succeeded", "", 3000)
                                                )
                                                .finally(() => setUploading(false))
                                        } else {
                                            setUploadName(undefined)
                                            setUploadContent(undefined)
                                        }
                                    },
                                    error =>
                                        dispatchError(
                                            dispatch,
                                            error,
                                            "VALIDATE_FILE",
                                            "Cannot validate file for import"
                                        )
                                )
                            }}
                        >
                            Import
                            {uploading && (
                                <>
                                    {" "}
                                    <Spinner size="md" />
                                </>
                            )}
                        </Button>
                    </FlexItem>
                </Flex>
            </FormGroup>
        </Form>
    )
}
