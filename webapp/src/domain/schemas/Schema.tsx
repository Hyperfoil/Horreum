import React, { useState, useEffect } from "react"
import { useParams } from "react-router"
import { useSelector, useDispatch } from "react-redux"

import {
    Alert,
    Button,
    Card,
    CardBody,
    Form,
    FormGroup,
    Spinner,
    TextArea,
    TextInput,
    Tooltip,
    Bullseye,
} from "@patternfly/react-core"
import { ImportIcon } from "@patternfly/react-icons"
import jsonpath from "jsonpath"

import * as actions from "./actions"
import * as selectors from "./selectors"
import * as api from "./api"
import { defaultTeamSelector, teamsSelector, teamToName, useTester } from "../../auth"
import { alertAction, constraintValidationFormatter, dispatchInfo } from "../../alerts"

import { toString } from "../../components/Editor"
import Editor from "../../components/Editor/monaco/Editor"
import AccessIcon from "../../components/AccessIcon"
import AccessChoice from "../../components/AccessChoice"
import SavedTabs, { SavedTab } from "../../components/SavedTabs"
import TeamSelect from "../../components/TeamSelect"
import { Extractor } from "../../components/Accessors"
import { Schema as SchemaDef, SchemaDispatch } from "./reducers"
import Extractors from "./Extractors"

type SchemaParams = {
    schemaId: string
}

type GeneralProps = {
    schema: SchemaDef | undefined
    onChange(partialSchema: SchemaDef): void
    getUri?(): string
}

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
        token: null,
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
            <FormGroup label="URI" isRequired={true} fieldId="schemaURI" helperTextInvalid="Must provide a valid URI">
                <>
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
                            validated={(schema.uri && schema.uri !== "") || !isTester ? "default" : "error"}
                            onChange={value => {
                                onChange({ uri: value })
                            }}
                            placeholder={isTester ? "Click button to import" : ""}
                            style={{ width: "1200px" }}
                        />
                    </div>
                    {schema.uri && otherUri && otherUri !== schema.uri && (
                        <Alert variant="warning" title="Schema $id in JSON is not matching to this URI" />
                    )}
                    {importFailed && <Alert variant="warning" title="Schema does not have $id - cannot import." />}
                </>
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
                        checkedValue={schema.access}
                        onChange={access => {
                            onChange({ access })
                        }}
                    />
                ) : (
                    <AccessIcon access={schema.access} />
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

    const dispatch = useDispatch()
    const thunkDispatch = useDispatch<SchemaDispatch>()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        if (schemaId >= 0) {
            setLoading(true)
            thunkDispatch(actions.getById(schemaId))
                .catch(e => {
                    dispatch(alertAction("FAILED_LOADING_SCHEMA", "Failed loading schema " + schemaId, e))
                })
                .finally(() => setLoading(false))
        } else {
            setLoading(false)
        }
    }, [dispatch, thunkDispatch, schemaId, teams])
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
        try {
            var schemaUri = jsonpath.value(JSON.parse(content), "$['$id']")
        } catch (e) {}
        return schemaUri || undefined
    }

    const isTester = useTester(schema?.owner)

    const [extractors, setExtractors] = useState<Extractor[]>([])
    const [originalExtractors, setOriginalExtractors] = useState<Extractor[]>([])
    useEffect(() => {
        if (schemaId >= 0) {
            api.listExtractors(schemaId).then(result => {
                const exs = result
                    .map((e: Extractor) => {
                        e.newName = e.accessor
                        return e
                    })
                    .sort((a: Extractor, b: Extractor) => a.accessor.localeCompare(b.accessor))
                setExtractors(exs)
                setOriginalExtractors(JSON.parse(JSON.stringify(exs))) // deep copy
            })
        }
    }, [schemaId, teams])
    const uri = currentSchema?.uri
    useEffect(() => {
        if (uri) {
            setExtractors(extractors.map(e => ({ ...e, schema: uri })))
        }
    }, [uri])

    const save = () => {
        let newSchema = {
            id: schemaId,
            ...currentSchema,
            schema: editorSchema ? JSON.parse(editorSchema) : null,
            token: null,
        } as SchemaDef
        // do not update the main schema when just changing extractors
        return (modified ? thunkDispatch(actions.add(newSchema)) : Promise.resolve(schemaId))
            .then(id => {
                setSchemaId(id)
            })
            .then(() =>
                Promise.all(
                    extractors
                        .filter(e => e.changed || (e.deleted && e.accessor !== ""))
                        .map(e => api.addOrUpdateExtractor(e))
                )
            )
            .then(() => setOriginalExtractors(JSON.parse(JSON.stringify(extractors))))
            .catch(e => {
                dispatch(
                    alertAction(
                        "SAVE_SCHEMA",
                        "Failed to save the schema",
                        e,
                        constraintValidationFormatter("the saved schema")
                    )
                )
            })
    }
    return (
        <React.Fragment>
            <Card style={{ flexGrow: 1 }}>
                {loading && (
                    <Bullseye>
                        <Spinner />
                    </Bullseye>
                )}
                {!loading && (
                    <>
                        <CardBody>
                            <SavedTabs
                                afterSave={() => {
                                    setModified(false)
                                    dispatchInfo(
                                        dispatch,
                                        "SAVE_SCHEMA",
                                        "Saved!",
                                        "Schema was successfully saved",
                                        3000
                                    )
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
                                                    readOnly: !isTester,
                                                }}
                                            />
                                        </div>
                                    )}
                                    {!editorSchema && (
                                        <>
                                            This schema does not have a validation JSON schema defined.
                                            <br />
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
                                        </>
                                    )}
                                </SavedTab>
                                <SavedTab
                                    title="Schema extractors"
                                    fragment="extractors"
                                    onSave={save}
                                    onReset={() => {
                                        setCurrentSchema(schema)
                                        setExtractors(originalExtractors)
                                    }}
                                    isModified={() => modified}
                                >
                                    <Extractors
                                        uri={currentSchema?.uri || ""}
                                        extractors={extractors}
                                        setExtractors={extractors => {
                                            setExtractors(extractors)
                                            setModified(true)
                                        }}
                                        isTester={isTester}
                                    />
                                    {isTester && (
                                        <>
                                            <Button
                                                isDisabled={!currentSchema?.uri}
                                                onClick={() => {
                                                    if (currentSchema?.uri) {
                                                        setExtractors([
                                                            ...extractors,
                                                            { accessor: "", schema: currentSchema?.uri },
                                                        ])
                                                    }
                                                }}
                                            >
                                                Add extractor
                                            </Button>
                                            {!currentSchema?.uri && (
                                                <>
                                                    <br />
                                                    <span style={{ color: "red" }}>Please define an URI first.</span>
                                                </>
                                            )}
                                        </>
                                    )}
                                </SavedTab>
                            </SavedTabs>
                        </CardBody>
                    </>
                )}
            </Card>
        </React.Fragment>
    )
}
