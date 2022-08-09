import { useEffect, useState, useRef } from "react"
import { useParams } from "react-router"
import { useSelector, useDispatch } from "react-redux"

import {
    Alert,
    Bullseye,
    Button,
    Card,
    CardBody,
    Form,
    FormGroup,
    PageSection,
    Spinner,
    TextArea,
    TextInput,
    Tooltip,
} from "@patternfly/react-core"
import { ImportIcon } from "@patternfly/react-icons"
import jsonpath from "jsonpath"

import * as actions from "./actions"
import * as selectors from "./selectors"
import { defaultTeamSelector, teamsSelector, teamToName, useTester } from "../../auth"
import { dispatchInfo } from "../../alerts"
import { noop } from "../../utils"

import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import AccessIcon from "../../components/AccessIcon"
import AccessChoice from "../../components/AccessChoice"
import SavedTabs, { SavedTab, TabFunctions, modifiedFunc, resetFunc, saveFunc } from "../../components/SavedTabs"
import TeamSelect from "../../components/TeamSelect"
import { SchemaDispatch } from "./reducers"
import Transformers from "./Transformers"
import Labels from "./Labels"
import { Access, Schema as SchemaDef } from "../../api"

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
        id: 0,
        name: "",
        description: "",
        uri: "",
        schema: {},
        owner: defaultTeam || "",
        access: 2,
    }
    const onChange = (override: Partial<SchemaDef>) => {
        props.onChange({
            ...schema,
            ...override,
        })
    }

    const otherUri = props.getUri ? props.getUri() : ""
    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
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
    const params = useParams<SchemaParams>()
    const [schemaId, setSchemaId] = useState(params.schemaId === "_new" ? -1 : Number.parseInt(params.schemaId))
    const schema = useSelector(selectors.getById(schemaId))
    const [loading, setLoading] = useState(true)
    const [editorSchema, setEditorSchema] = useState(schema?.schema ? toString(schema?.schema) : undefined)
    const [currentSchema, setCurrentSchema] = useState(schema)
    const [modified, setModified] = useState(false)

    const dispatch = useDispatch<SchemaDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        if (schemaId >= 0) {
            setLoading(true)
            dispatch(actions.getById(schemaId))
                .catch(noop)
                .finally(() => setLoading(false))
        } else {
            setLoading(false)
        }
    }, [dispatch, dispatch, schemaId, teams])
    useEffect(() => {
        document.title = (schemaId < 0 ? "New schema" : schema?.name || "(unknown schema)") + " | Horreum"
        setCurrentSchema(schema)
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

    // any tester can save to add new labels/transformers
    const isTester = useTester()
    const isTesterForSchema = useTester(schema?.owner)

    const save = () => {
        if (!modified) {
            return Promise.resolve(schemaId)
        }
        const newSchema = {
            id: schemaId,
            ...currentSchema,
            schema: editorSchema ? JSON.parse(editorSchema) : null,
        } as SchemaDef
        return dispatch(actions.add(newSchema))
            .then(id => {
                setSchemaId(id)
            })
            .catch(noop)
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
                    <CardBody>
                        <SavedTabs
                            afterSave={() => {
                                setModified(false)
                                dispatchInfo(dispatch, "SAVE_SCHEMA", "Saved!", "Schema was successfully saved", 3000)
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
                                    setCurrentSchema(schema)
                                }}
                                isModified={() => modified}
                            >
                                <General
                                    schema={currentSchema}
                                    getUri={editorSchema ? () => getUri(editorSchema) : undefined}
                                    onChange={schema => {
                                        setCurrentSchema(schema)
                                        setModified(true)
                                    }}
                                />
                            </SavedTab>
                            <SavedTab
                                title="JSON schema"
                                fragment="json-schema"
                                onSave={save}
                                onReset={() => {
                                    setCurrentSchema(schema)
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
                                {!editorSchema && (
                                    <>
                                        This schema does not have a validation JSON schema defined.
                                        <br />
                                        {isTesterForSchema && (
                                            <Button
                                                onClick={() => {
                                                    setEditorSchema(
                                                        JSON.stringify(
                                                            {
                                                                $id: currentSchema?.uri,
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
                        </SavedTabs>
                    </CardBody>
                </Card>
            )}
        </PageSection>
    )
}
