import {useEffect, useState, useRef, useContext} from "react"
import { useParams } from "react-router"
import { useSelector } from "react-redux"

import {
    Alert,
    Breadcrumb,
    BreadcrumbItem,
    Bullseye,
    Button,
    Card,
    CardBody,
    CardHeader,
    Form,
    FormGroup,
    PageSection,
    Spinner,
    TextArea,
    TextInput,
    Tooltip,
} from "@patternfly/react-core"
import { ImportIcon } from "@patternfly/react-icons"
import { Link } from "react-router-dom"
import jsonpath from "jsonpath"

import { defaultTeamSelector,  teamToName, useTester } from "../../auth"
import { noop } from "../../utils"

import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import AccessIcon from "../../components/AccessIcon"
import AccessChoice from "../../components/AccessChoice"
import SavedTabs, { SavedTab, TabFunctions, modifiedFunc, resetFunc, saveFunc } from "../../components/SavedTabs"
import TeamSelect from "../../components/TeamSelect"
import Transformers from "./Transformers"
import Labels from "./Labels"
import {Access, getSchema, Schema as SchemaDef, schemaApi} from "../../api"
import SchemaExportImport from "./SchemaExportImport"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type SchemaParams = {
    schemaId: string
}

type GeneralProps = {
    schema: SchemaDef | undefined
    onChange(partialSchema: SchemaDef): void
    getUri?(): string
}

const SUPPORTED_SCHEMES = ["uri:", "urn:", "http:", "https:", "ftp:", "file:", "jar:"]

function General(props: GeneralProps) {
    const defaultTeam = useSelector(defaultTeamSelector)
    const isTester = useTester(props.schema?.owner)
    const [importFailed, setImportFailed] = useState(false)

    const schema: SchemaDef = props.schema || {
        id: -1,
        uri: "",
        name: "",
        owner: defaultTeam || "",
        access: Access.Public,
    }
    const onChange = (override: Partial<SchemaDef>) => {
        props.onChange({
            ...schema,
            ...override,
        })
    }

    const otherUri = props.getUri ? props.getUri() : ""
    return (
        <Form isHorizontal={true} >
            <FormGroup
                label="Name"
                isRequired={true}
                fieldId="schemaName"
                helperText="names must be unique"
                helperTextInvalid="Name must be unique and not empty"
            >
                <TextInput
                    value={schema.name}
                    isRequired
                    type="text"
                    id="schemaName"
                    aria-describedby="name-helper"
                    name="schemaName"
                    validated={(schema.name && schema.name !== "") || !isTester ? "default" : "error"}
                    isReadOnly={!isTester}
                    onChange={value => {
                        onChange({ name: value })
                    }}
                />
            </FormGroup>
            <FormGroup label="URI" isRequired={true} fieldId="schemaURI">
                <div style={{ display: "flex" }}>
                    {!schema.uri && isTester && props.getUri !== undefined && (
                        <Tooltip content={"Import URI from the schema"}>
                            <Button
                                variant="control"
                                style={{ float: "left" }}
                                onClick={() => {
                                    if (props.getUri) {
                                        const newUri = props.getUri()
                                        if (!newUri) {
                                            setImportFailed(true)
                                            setInterval(() => setImportFailed(false), 5000)
                                        } else {
                                            onChange({ uri: newUri })
                                        }
                                    }
                                }}
                            >
                                <ImportIcon />
                            </Button>
                        </Tooltip>
                    )}
                    <TextInput
                        value={schema.uri || ""}
                        isRequired
                        type="text"
                        id="schemaURI"
                        name="schemaURI"
                        isReadOnly={!isTester}
                        validated={
                            (schema.uri && SUPPORTED_SCHEMES.some(s => schema.uri.startsWith(s))) || !isTester
                                ? "default"
                                : "error"
                        }
                        onChange={value => {
                            onChange({ uri: value })
                        }}
                        placeholder={isTester ? "Click button to import" : ""}
                        style={{ width: "1200px" }}
                    />
                </div>
                {schema.uri && !SUPPORTED_SCHEMES.some(s => schema.uri.startsWith(s)) && (
                    <Alert
                        variant="warning"
                        title={
                            <>
                                Please provide a valid URI starting with one of these schemes: <code>uri</code>,{" "}
                                <code>urn</code>, <code>http</code>, <code>https</code>, <code>ftp</code>,{" "}
                                <code>file</code> or <code>jar</code>
                            </>
                        }
                    />
                )}
                {schema.uri && otherUri && otherUri !== schema.uri && (
                    <Alert variant="warning" title="Schema $id in JSON is not matching to this URI" />
                )}
                {importFailed && <Alert variant="warning" title="Schema does not have $id - cannot import." />}
            </FormGroup>
            <FormGroup label="Description" fieldId="schemaDescription" helperText="" helperTextInvalid="">
                <TextArea
                    value={schema.description}
                    type="text"
                    id="schemaDescription"
                    aria-describedby="description-helper"
                    name="schemaDescription"
                    readOnly={!isTester}
                    onChange={value => {
                        onChange({ description: value })
                    }}
                />
            </FormGroup>
            <FormGroup label="Owner" fieldId="schemaOwner">
                {isTester ? (
                    <TeamSelect
                        includeGeneral={false}
                        selection={teamToName(schema.owner) || ""}
                        onSelect={selection => {
                            onChange({ owner: selection.key })
                        }}
                    />
                ) : (
                    <TextInput id="schemaOwner" value={teamToName(schema.owner) || ""} isReadOnly />
                )}
            </FormGroup>
            <FormGroup label="Access rights" fieldId="schemaAccess">
                {isTester ? (
                    <AccessChoice
                        checkedValue={schema.access as Access}
                        onChange={access => {
                            onChange({ access })
                        }}
                    />
                ) : (
                    <AccessIcon access={schema.access as Access} />
                )}
            </FormGroup>
        </Form>
    )
}

export default function Schema() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const params = useParams<SchemaParams>()
    const [schemaId, setSchemaId] = useState(params.schemaId === "_new" ? -1 : Number.parseInt(params.schemaId))
    const [schema, setSchema] = useState<SchemaDef | undefined>(undefined)
    const [loading, setLoading] = useState(true)
    const [editorSchema, setEditorSchema] = useState(schema?.schema ? toString(schema?.schema) : undefined)
    const [modifiedSchema, setModifiedSchema] = useState(schema)
    const [modified, setModified] = useState(false)

    // any tester can save to add new labels/transformers
    const isTester = useTester()
    const isTesterForSchema = useTester(schema?.owner)

    useEffect(() => {
        if (schemaId >= 0) {
            setLoading(true)
            getSchema(schemaId, alerting)
                .then(setSchema)
                .finally(() => setLoading(false))
        } else {
            setLoading(false)
        }
    }, [schemaId])
    useEffect(() => {
        document.title = (schemaId < 0 ? "New schema" : schema?.name || "(unknown schema)") + " | Horreum"
        setModifiedSchema(schema)
        setEditorSchema(schema?.schema ? toString(schema?.schema) : undefined)
    }, [schema, schemaId])
    // TODO: use this in reaction to editor change
    const getUri = (content?: string) => {
        if (!content) {
            return
        }
        let schemaUri
        try {
            schemaUri = jsonpath.value(JSON.parse(content), "$['$id']")
        } catch (e) {
            /* noop */
        }
        return schemaUri || undefined
    }



    const save = () => {
        if (!modified) {
            return Promise.resolve(schemaId)
        }
        const newSchema = {
            id: schemaId,
            ...modifiedSchema,
            schema: editorSchema ? JSON.parse(editorSchema) : null,
        } as SchemaDef

        return schemaApi.add(newSchema)
            .then(id=>  id,
                error => alerting.dispatchError(error, "SAVE_SCHEMA", "Failed to save schema")
            ).then(id => setSchemaId(id))
    }
    const transformersFuncsRef = useRef<TabFunctions>()
    const labelsFuncsRef = useRef<TabFunctions>()

    return (
        <PageSection>
            {loading && (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            )}
            {!loading && (
                <Card style={{ height: "100%" }}>
                    <CardHeader>
                        <Breadcrumb>
                            <BreadcrumbItem>
                                <Link to="/schema">Schemas</Link>
                            </BreadcrumbItem>
                            <BreadcrumbItem isActive>{schema?.name}</BreadcrumbItem>
                        </Breadcrumb>
                    </CardHeader>
                    <CardBody>
                        <SavedTabs
                            afterSave={() => {
                                setModified(false)
                                alerting.dispatchInfo("SAVE_SCHEMA", "Saved!", "Schema was successfully saved", 3000)
                            }}
                            afterReset={() => {
                                setModified(false)
                            }}
                            canSave={isTester}
                        >
                            <SavedTab
                                title="General"
                                fragment="general"
                                onSave={save}
                                onReset={() => {
                                    setModifiedSchema(schema)
                                }}
                                isModified={() => modified}
                            >
                                <General
                                    schema={modifiedSchema}
                                    getUri={editorSchema ? () => getUri(editorSchema) : undefined}
                                    onChange={schema => {
                                        setModifiedSchema(schema)
                                        setModified(true)
                                    }}
                                />
                            </SavedTab>
                            <SavedTab
                                title="JSON schema"
                                fragment="json-schema"
                                onSave={save}
                                onReset={() => {
                                    setModifiedSchema(schema)
                                    setEditorSchema(schema?.schema ? toString(schema?.schema) : undefined)
                                }}
                                isModified={() => modified}
                            >
                                {editorSchema !== undefined && (
                                    <div style={{ height: "600px" }}>
                                        <Editor
                                            value={editorSchema}
                                            onChange={value => {
                                                setEditorSchema(value)
                                                setModified(true)
                                            }}
                                            options={{
                                                mode: "application/ld+json",
                                                readOnly: !isTesterForSchema,
                                            }}
                                        />
                                    </div>
                                )}
                                {editorSchema === undefined && (
                                    <>
                                        This schema does not have a validation JSON schema defined.
                                        <br />
                                        {isTesterForSchema && (
                                            <Button
                                                onClick={() => {
                                                    setEditorSchema(
                                                        JSON.stringify(
                                                            {
                                                                $id: modifiedSchema?.uri,
                                                                $schema: "http://json-schema.org/draft-07/schema#",
                                                                type: "object",
                                                            },
                                                            undefined,
                                                            2
                                                        )
                                                    )
                                                    setModified(true)
                                                }}
                                            >
                                                Add validation schema
                                            </Button>
                                        )}
                                    </>
                                )}
                            </SavedTab>
                            <SavedTab
                                title="Transformers"
                                fragment="transformers"
                                onSave={saveFunc(transformersFuncsRef)}
                                onReset={resetFunc(transformersFuncsRef)}
                                isModified={modifiedFunc(transformersFuncsRef)}
                            >
                                <Transformers
                                    schemaId={schemaId}
                                    schemaUri={schema?.uri || ""}
                                    funcsRef={transformersFuncsRef}
                                />
                            </SavedTab>
                            <SavedTab
                                title="Labels"
                                fragment="labels"
                                onSave={saveFunc(labelsFuncsRef)}
                                onReset={resetFunc(labelsFuncsRef)}
                                isModified={modifiedFunc(labelsFuncsRef)}
                            >
                                <Labels schemaId={schemaId} schemaUri={schema?.uri || ""} funcsRef={labelsFuncsRef} />
                            </SavedTab>
                            <SavedTab
                                title="Export"
                                fragment="export"
                                onSave={() => Promise.resolve()}
                                onReset={noop}
                                isHidden={!isTester}
                                isModified={() => false}
                            >
                                <SchemaExportImport id={schemaId} name={schema?.name || "schema"} />
                            </SavedTab>
                        </SavedTabs>
                    </CardBody>
                </Card>
            )}
        </PageSection>
    )
}
