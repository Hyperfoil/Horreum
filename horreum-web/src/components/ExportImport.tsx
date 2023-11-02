import {useContext, useState} from "react"

import { Button, FileUpload, Flex, FlexItem, Form, FormGroup, Spinner,
    HelperText,
    HelperTextItem,
    FormHelperText } from "@patternfly/react-core"

import ExportButton from "./ExportButton"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
import { SchemaExport, TestExport } from "../generated";

type ExportImportProps = {
    name: string
    export(): Promise<any>
    import(cfg: SchemaExport | TestExport): Promise<void>
    validate(cfg: SchemaExport | TestExport): Promise<boolean>
}

export default function ExportImport(props: ExportImportProps) {
    const [uploadName, setUploadName] = useState<string>()
    const [loading, setLoading] = useState(false)
    const [uploadContent, setUploadContent] = useState<SchemaExport | TestExport>()
    const [uploading, setUploading] = useState(false)
    const [parseError, setParseError] = useState<any>(undefined)
    const { alerting } = useContext(AppContext) as AppContextType;

    return (
        <Form isHorizontal>
            <FormGroup label="Export" fieldId="export">
                <ExportButton name={props.name} export={props.export} />
            </FormGroup>
            <FormGroup
                label="Import"
                fieldId="import"
            >
                <FormHelperText>
                    <HelperText>
                        <HelperTextItem variant={parseError ? "error" : "default"}>
                        {parseError ? parseError?.toString() : ""}
                        </HelperTextItem>
                    </HelperText>
                </FormHelperText>
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
                                                setUploadContent(JSON.parse(cfg))
                                            } catch (e) {
                                                setParseError(e)
                                                return
                                            }
                                        },
                                        error => {
                                            alerting.dispatchError( error, "LOAD_FILE", "Cannot load file for import")
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
                                                    alerting.dispatchInfo("IMPORT", "Import succeeded", "", 3000)
                                                )
                                                .finally(() => setUploading(false))
                                        } else {
                                            setUploadName(undefined)
                                            setUploadContent(undefined)
                                        }
                                    },
                                    error =>
                                        alerting.dispatchError(
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
