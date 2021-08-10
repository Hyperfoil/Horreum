import React, { useState, useRef, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector, useDispatch } from 'react-redux'

import {
    Alert,
    Button,
    Card,
    CardBody,
    CardFooter,
    Form,
    ActionGroup,
    FormGroup,
    Spinner,
    Tab,
    Tabs,
    TextArea,
    TextInput,
    Tooltip,
    Bullseye,
} from '@patternfly/react-core';
import { NavLink } from 'react-router-dom';
import {
    ImportIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import jsonpath from 'jsonpath';

import * as actions from './actions';
import * as selectors from './selectors';
import * as api from './api';
import { defaultRoleSelector, rolesSelector, roleToName, useTester } from '../../auth'
import {
   alertAction,
   constraintValidationFormatter,
   dispatchInfo,
} from "../../alerts"

import { toString } from '../../components/Editor';
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor';
import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import JsonPathDocsLink from '../../components/JsonPathDocsLink'
import OwnerSelect from '../../components/OwnerSelect'
import { Extractor } from '../../components/Accessors';
import { Schema as SchemaDef, SchemaDispatch } from './reducers';

type SchemaParams = {
    schemaId: string,
}

type GeneralProps = {
    schema: SchemaDef | undefined
    onChange(partialSchema: SchemaDef): void,
    getUri?(): string,
}

function General(props: GeneralProps) {
    const defaultRole = useSelector(defaultRoleSelector)
    const isTester = useTester(props.schema?.owner)
    const [importFailed, setImportFailed] = useState(false)

    const schema: SchemaDef = props.schema || {
        id: 0,
        name: "",
        description: "",
        uri: "",
        schema: {},
        owner: defaultRole || "",
        access: 2,
        token: null,
    }
    const onChange = (override: Partial<SchemaDef>) => {
        props.onChange({
            ...schema, ...override
        })
    }

    const otherUri = props.getUri ? props.getUri() : ""
    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
            <FormGroup label="Name" isRequired={true} fieldId="schemaName" helperText="names must be unique" helperTextInvalid="Name must be unique and not empty">
                <TextInput
                    value={schema.name}
                    isRequired
                    type="text"
                    id="schemaName"
                    aria-describedby="name-helper"
                    name="schemaName"
                    validated={ (schema.name && schema.name !== "") || !isTester ? "default" : "error"}
                    isReadOnly={ !isTester }
                    onChange={value => {
                        onChange({ name: value})
                    }}
                />
            </FormGroup>
            <FormGroup label="URI" isRequired={true} fieldId="schemaURI" helperTextInvalid="Must provide a valid URI">
                <>
                <div style={{ display: "flex" }}>
                { !schema.uri && isTester && props.getUri !== undefined &&
                <Tooltip content={"Import URI from the schema"}>
                    <Button variant="control"
                            style={{ float: "left" }}
                            onClick={ () => {
                                if (props.getUri) {
                                    const newUri = props.getUri();
                                    if (!newUri) {
                                        setImportFailed(true)
                                        setInterval(() => setImportFailed(false), 5000)
                                    } else {
                                        onChange({ uri: newUri })
                                    }
                                }
                            }}
                    ><ImportIcon /></Button>
                </Tooltip>
                }
                <TextInput
                    value={schema.uri || ""}
                    isRequired
                    type="text"
                    id="schemaURI"
                    name="schemaURI"
                    isReadOnly={ !isTester }
                    validated={(schema.uri && schema.uri !== "") || !isTester ? "default" : "error"}
                    onChange={value => {
                        onChange({ uri: value })
                    }}
                    placeholder={ isTester ? "Click button to import" : "" }
                    style={{ width: "1200px" }}
                />
                </div>
                { schema.uri && otherUri && otherUri !== schema.uri &&
                    <Alert variant="warning" title="Schema $id in JSON is not matching to this URI" />
                }
                { importFailed &&
                    <Alert variant="warning" title="Schema does not have $id - cannot import." />
                }
                </>
            </FormGroup>
            <FormGroup label="Description" fieldId="schemaDescription" helperText="" helperTextInvalid="">
                <TextArea
                    value={schema.description}
                    type="text"
                    id="schemaDescription"
                    aria-describedby="description-helper"
                    name="schemaDescription"
                    readOnly={ !isTester }
                    onChange={value => {
                        onChange({ description: value })
                    }}
                />
            </FormGroup>
            <FormGroup label="Owner" fieldId="schemaOwner">
                { isTester ? (
                    <OwnerSelect includeGeneral={false}
                                selection={roleToName(schema.owner) || ""}
                                onSelect={selection => {
                                    onChange({ owner: selection.key })
                                }} />
                ) : (
                    <TextInput id="schemaOwner" value={roleToName(schema.owner) || ""} isReadOnly />
                )}
            </FormGroup>
            <FormGroup label="Access rights" fieldId="schemaAccess">
                { isTester ? (
                    <AccessChoice checkedValue={schema.access} onChange={access => {
                        onChange({ access })
                    }} />
                ) : (
                    <AccessIcon access={schema.access} />
                )}
            </FormGroup>
        </Form>)
}

type ExtractorsProps = {
    extractors: Extractor[]
    setExtractors(extractors: Extractor[]): void,
    isTester: boolean,
}

function Extractors(props: ExtractorsProps) {
    return (<>{ props.extractors.filter((e: Extractor) => !e.deleted).map((e: Extractor) =>
        <Form isHorizontal={true} style={{ gridGap: "2px", marginBottom: "10px", paddingRight: "40px", position: "relative" }}>
            <FormGroup label="Accessor" fieldId="accessor">
                <TextInput id="accessor"
                            value={e.newName || ""}
                            isReadOnly={!props.isTester}
                            onChange={newValue => {
                    e.newName = newValue
                    e.changed = true
                    props.setExtractors([...props.extractors])
                }}/>
            </FormGroup>
            <FormGroup
                label={
                    <>JSON path <JsonPathDocsLink /></>
                }
                fieldId="jsonpath"
                validated={ !e.validationResult || e.validationResult.valid ? "default" : "error"}
                helperTextInvalid={ e.validationResult?.reason || ""  }>
                <TextInput id="jsonpath"
                            value={e.jsonpath || ""}
                            isReadOnly={!props.isTester}
                            onChange={newValue => {
                    e.jsonpath = newValue;
                    e.changed = true
                    e.validationResult = undefined
                    props.setExtractors([...props.extractors])
                    if (e.validationTimer) {
                        clearTimeout(e.validationTimer)
                    }
                    e.validationTimer = window.setTimeout(() => {
                        if (e.jsonpath) {
                            api.testJsonPath(e.jsonpath).then(result => {
                                e.validationResult = result
                                props.setExtractors([...props.extractors])
                            })
                        }
                    }, 1000)
                }}/>
            </FormGroup>
            { props.isTester &&
            <Button variant="plain" style={{ position: "absolute", right: "0px", top: "22px" }}
                    onClick={ () => {
                e.deleted = true;
                props.setExtractors([...props.extractors])
            }}>
                <OutlinedTimesCircleIcon style={{color: "#a30000"}} />
            </Button>
            }
        </Form>
    )} </>)
}

export default function Schema() {
    const { schemaId } = useParams<SchemaParams>();
    const schema = useSelector(selectors.getById(Number.parseInt(schemaId)))
    const [loading, setLoading] = useState(true)
    const [editorSchema, setEditorSchema] = useState(schema?.schema ? toString(schema?.schema) : undefined)
    const [currentSchema, setCurrentSchema] = useState(schema)

    const dispatch = useDispatch();
    const thunkDispatch = useDispatch<SchemaDispatch>()
    const roles = useSelector(rolesSelector)
    useEffect(() => {
        if (schemaId !== "_new") {
            setLoading(true)
            thunkDispatch(actions.getById(Number.parseInt(schemaId))).catch(e => {
                dispatch(alertAction("FAILED_LOADING_SCHEMA", "Failed loading schema " + schemaId, e))
            }).finally(() => setLoading(false))
        } else {
            setLoading(false)
        }
    }, [dispatch, thunkDispatch, schemaId, roles])
    useEffect(() => {
        document.title = (schemaId === "_new" ? "New schema" : schema?.name || "(unknown schema)")  + " | Horreum"
        setCurrentSchema(schema)
        setEditorSchema(schema?.schema ? toString(schema?.schema) : undefined)
    }, [schema, schemaId])
     // TODO editor types
    const editor = useRef<ValueGetter>();
    // TODO: use this in reaction to editor change
    const getUri = (content?: string) => {
        if (!content) {
            return
        }
        try {
           var schemaUri = jsonpath.value(JSON.parse(content), "$['$id']")
        } catch (e) {
        }
        return schemaUri || undefined;
    }

    const isTester = useTester(schema?.owner)

    const [activeTab, setActiveTab] = useState(0)
    const [extractors, setExtractors] = useState<Extractor[]>([])
    useEffect(() => {
        if (schemaId !== "_new") {
            api.listExtractors(Number.parseInt(schemaId)).then(result => setExtractors(result.map((e: Extractor) => {
                e.newName = e.accessor
                return e
            }).sort((a: Extractor, b: Extractor) => a.accessor.localeCompare(b.accessor))))
        }
    }, [schemaId])
    useEffect(() => {
        if (currentSchema?.uri) {
            setExtractors(extractors.map(e => ({ ...e, schema: currentSchema?.uri })))
        }
    }, [currentSchema?.uri])

    const save = () => {
        let savedSchema;
        if (activeTab === 1) {
           savedSchema = editor.current?.getValue()
        } else {
           savedSchema = editorSchema
        }
        let newSchema = {
            id: schemaId !== "_new" ? parseInt(schemaId) : 0,
            ...currentSchema,
            schema: savedSchema ? JSON.parse(savedSchema) : null,
            token: null
        } as SchemaDef
        thunkDispatch(actions.add(newSchema))
               .then(() => Promise.all(extractors.filter(e => e.changed || (e.deleted && e.accessor !== "")).map(e => api.addOrUpdateExtractor(e))))
               .then(() => dispatchInfo(dispatch, "SAVE_SCHEMA", "Saved!", "Schema was successfully saved", 3000))
               .catch(e => {
                  dispatch(alertAction("SAVE_SCHEMA", "Failed to save the schema", e, constraintValidationFormatter("the saved schema")))
               })
    }
    return (
        <React.Fragment>
            <Card style={{ flexGrow: 1 }}>
                { loading && (<Bullseye><Spinner /></Bullseye>) }
                { !loading && (<>
                <CardBody>
                    <Tabs
                        activeKey={activeTab}
                        onSelect={(_, index) => {
                            if (activeTab === 1) {
                                /* When we switch tab the editor gets unmounted; getValue() would return empty string */
                                const value = editor.current?.getValue()
                                console.log(value)
                                if (value) {
                                setEditorSchema(value);
                                }
                            }
                            setActiveTab(index as number)
                        }}
                    >
                        <Tab key="general" eventKey={0} title="General">
                            <General
                                schema={ currentSchema }
                                getUri={ editorSchema ? () => getUri(editorSchema) : undefined }
                                onChange={ setCurrentSchema }
                            />
                        </Tab>
                        <Tab key="schema" eventKey={1} title="JSON schema" style={{ height: "100%" }}>
                            { editorSchema &&
                                <div style={{ height: "600px" }}>
                                    <Editor
                                        value={editorSchema}
                                        setValueGetter={e => { editor.current = e }}
                                        options={{
                                            mode: "application/ld+json",
                                            readOnly: !isTester
                                        }}
                                    />
                                </div>
                            }
                            { !editorSchema && <>
                                This schema does not have a validation JSON schema defined.<br />
                                <Button onClick={ () => {
                                    setEditorSchema(JSON.stringify({
                                        "$id": currentSchema?.uri,
                                        "$schema": "http://json-schema.org/draft-07/schema#",
                                        "type": "object"
                                    }, undefined, 2))
                                }}>Add validation schema</Button>
                            </>}
                        </Tab>
                        <Tab key="extractors" eventKey={2} title="Schema extractors">
                            <Extractors
                                extractors={extractors}
                                setExtractors={setExtractors}
                                isTester={isTester}
                            />
                            { isTester && <>
                                <Button
                                    isDisabled={!currentSchema?.uri}
                                    onClick={() => {
                                        if (currentSchema?.uri) {
                                            setExtractors([...extractors, { accessor: "", schema: currentSchema?.uri }])
                                        }
                                    }} >Add extractor</Button>
                                { !currentSchema?.uri && <><br /><span style={{ color: "red"}}>Please define an URI first.</span></> }
                            </> }
                         </Tab>
                    </Tabs>
                </CardBody>
                { isTester &&
                <CardFooter>
                  <ActionGroup style={{ marginTop: 0 }}>
                      <Button
                        variant="primary"
                        onClick={save}
                      >Save</Button>
                      <NavLink className="pf-c-button pf-m-secondary" to="/schema/">
                          Cancel
                      </NavLink>
                  </ActionGroup>
                </CardFooter>
                }
                </>)}
            </Card>
        </React.Fragment>
    )
}
