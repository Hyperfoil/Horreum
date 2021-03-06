import React, { useState, useRef, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector, useDispatch } from 'react-redux'

import {
    Alert,
    Button,
    Card,
    CardHeader,
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
    Toolbar,
    ToolbarContent,
    ToolbarItem,
    Tooltip,
    Bullseye,
} from '@patternfly/react-core';
import { NavLink, Redirect } from 'react-router-dom';
import {
    ImportIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import jsonpath from 'jsonpath';

import * as actions from './actions';
import * as selectors from './selectors';
import * as api from './api';
import { Access, defaultRoleSelector, rolesSelector, roleToName, useTester } from '../../auth'
import {
   alertAction,
   constraintValidationFormatter,
} from "../../alerts"

import { toString } from '../../components/Editor';
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor';
import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import OwnerSelect from '../../components/OwnerSelect'
import { Extractor } from '../../components/Accessors';
import { SchemaDispatch } from './reducers';

type SchemaParams = {
    schemaId: string,
}

export default function Schema() {
    const { schemaId } = useParams<SchemaParams>();
    const schema = useSelector(selectors.getById(Number.parseInt(schemaId)))
    const [loading, setLoading] = useState(true)
    const [name, setName] = useState("")
    const [description, setDescription] = useState("");
    const [editorSchema, setEditorSchema] = useState(schema?.schema ? toString(schema.schema) : undefined)

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
        setName(schema?.name || "");
        setDescription(schema?.description || "")
        setUri(schema?.uri || "")
        if (schema && schema.owner) {
            setOwner(schema.owner)
        }
        if (schema && schema.access) {
            setAccess(schema.access)
        }
        setEditorSchema(schema?.schema ? toString(schema.schema) : undefined)
    }, [schema, schemaId])
     // TODO editor types
    const editor = useRef<ValueGetter>();
    const [uri, setUri] = useState(schema?.uri)
    const [uriMatching, setUriMatching] = useState(true)
    const [importFailed, setImportFailed] = useState(false)
    // TODO: use this in reaction to editor change
    const parseUri = (newSchema?: string) => {
        if (!newSchema) {
            return
        }
        try {
           var schemaUri = jsonpath.value(JSON.parse(newSchema), "$['$id']")
        } catch (e) {
        }
        if (!schemaUri || schemaUri === "") {
           setImportFailed(true)
           setInterval(() => setImportFailed(false), 5000)
        } else if (!uri || uri === "") {
           setUri(schemaUri);
           setUriMatching(true)
        } else {
           setUriMatching(uri === schemaUri)
        }
    }
    const checkUri = (newUri: string) => {
        const currentSchema = editor.current?.getValue() || "{}"
        try {
           var schemaUri = jsonpath.value(JSON.parse(currentSchema), "$['$id']")
        } catch (e) {
        }
        if (!schemaUri || schemaUri === "") {
           return // nothing to do
        } else {
           setUriMatching(newUri === schemaUri)
        }
    }

    const isTester = useTester(schema?.owner)
    const defaultRole = useSelector(defaultRoleSelector)
    const [access, setAccess] = useState<Access>(0)
    const [owner, setOwner] = useState(defaultRole)
    const [goBack, setGoBack] = useState(false)
    useEffect(() => setOwner(defaultRole), [ defaultRole])

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
    return (
        <React.Fragment>
            { goBack && <Redirect to='/schema' /> }
            <Card style={{ flexGrow: 1 }}>
                { loading && (<Bullseye><Spinner /></Bullseye>) }
                { !loading && (<>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                      <ToolbarContent>
                        <ToolbarItem aria-label="form">
                            <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
                                <FormGroup label="Name" isRequired={true} fieldId="schemaName" helperText="names must be unique" helperTextInvalid="Name must be unique and not empty">
                                    <TextInput
                                        value={name}
                                        isRequired
                                        type="text"
                                        id="schemaName"
                                        aria-describedby="name-helper"
                                        name="schemaName"
                                        validated={ (name && name !== "") || !isTester ? "default" : "error"}
                                        isReadOnly={ !isTester }
                                        onChange={e => setName(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="URI" isRequired={true} fieldId="schemaURI" helperTextInvalid="Must provide a valid URI">
                                   <>
                                   <div style={{ display: "flex" }}>
                                   { (!uri || uri === "") && isTester && editor.current?.getValue() &&
                                   <Tooltip content={"Import URI from the schema"}>
                                      <Button variant="control"
                                              style={{ float: "left" }}
                                              onClick={ () => parseUri(editor.current?.getValue()) }
                                      ><ImportIcon /></Button>
                                   </Tooltip>
                                   }
                                   <TextInput
                                        value={uri || ""}
                                        isRequired
                                        type="text"
                                        id="schemaURI"
                                        name="schemaURI"
                                        isReadOnly={ !isTester }
                                        validated={(uri && uri !== "") || !isTester ? "default" : "error"}
                                        onChange={e => {
                                            setUri(e)
                                            checkUri(e)
                                        }}
                                        placeholder={ isTester ? "Click button to import" : "" }
                                        style={{ width: "1200px" }}
                                   />
                                   </div>
                                   { uriMatching ||
                                       <Alert variant="warning" title="Schema $id in JSON is not matching to this URI" />
                                   }
                                   { importFailed &&
                                       <Alert variant="warning" title="Schema does not have $id - cannot import." />
                                   }
                                   </>
                                </FormGroup>
                                <FormGroup label="Description" fieldId="schemaDescription" helperText="" helperTextInvalid="">
                                    <TextArea
                                        value={description}
                                        type="text"
                                        id="schemaDescription"
                                        aria-describedby="description-helper"
                                        name="schemaDescription"
                                        readOnly={ !isTester }
                                        onChange={e => setDescription(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="Owner" fieldId="schemaOwner">
                                   { isTester ? (
                                      <OwnerSelect includeGeneral={false}
                                                   selection={roleToName(owner) || ""}
                                                   onSelect={selection => setOwner(selection.key)} />
                                   ) : (
                                      <TextInput id="schemaOwner" value={roleToName(owner) || ""} isReadOnly />
                                   )}
                                </FormGroup>
                                <FormGroup label="Access rights" fieldId="schemaAccess">
                                   { isTester ? (
                                      <AccessChoice checkedValue={access} onChange={setAccess} />
                                   ) : (
                                      <AccessIcon access={access} />
                                   )}
                                </FormGroup>
                            </Form>
                        </ToolbarItem>
                      </ToolbarContent>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Tabs>
                       <Tab key="schema" eventKey={0} title="JSON schema" style={{ height: "100%" }} onClick={ () => setActiveTab(0) }/>
                       <Tab key="extractors" eventKey={1} title="Schema extractors" onClick={ () => {
                           /* When we switch tab the editor gets unmounted; getValue() would return empty string */
                           const value = editor.current?.getValue()
                           if (value) {
                              setEditorSchema(value);
                           }
                           setActiveTab(1)
                        } }/>
                    </Tabs>
                    { activeTab === 0 && <>
                        { editorSchema &&
                            <div style={{ height: "600px" }}>
                                <Editor
                                    value={editorSchema}
                                    setValueGetter={e => { editor.current = e }}
                                    options={{ mode: "application/ld+json" }}
                                />
                            </div>
                        }
                        { !editorSchema && <>
                            This schema does not have a validation JSON schema defined.<br />
                            <Button onClick={ () => {
                                setEditorSchema(JSON.stringify({
                                    "$id": uri,
                                    "$schema": "http://json-schema.org/draft-07/schema#",
                                    "type": "object"
                                }, undefined, 2))
                            }}>Add validation schema</Button>
                        </>}
                    </>}
                    { activeTab === 1 && extractors.filter((e: Extractor) => !e.deleted).map((e: Extractor) => (<>
                       <Form isHorizontal={true} style={{ gridGap: "2px", marginBottom: "10px", paddingRight: "40px", position: "relative" }}>
                          <FormGroup label="Accessor" fieldId="accessor">
                              <TextInput id="accessor"
                                         value={e.newName || ""}
                                         isReadOnly={!isTester}
                                         onChange={newValue => {
                                 e.newName = newValue
                                 e.changed = true
                                 setExtractors([...extractors])
                              }}/>
                          </FormGroup>
                          <FormGroup label="JSON path" fieldId="jsonpath"
                                     validated={ !(e.jsonpath && e.jsonpath.trim().startsWith("$")) && (!e.validationResult || e.validationResult.valid) ? "default" : "error"}
                                     helperTextInvalid={ e.jsonpath && e.jsonpath.trim().startsWith("$") ? "JSON path must not start with '$'" : (e.validationResult?.reason || "")  }>
                              <TextInput id="jsonpath"
                                         value={e.jsonpath || ""}
                                         isReadOnly={!isTester}
                                         validated={!e.jsonpath || !e.jsonpath.trim().startsWith("$") ? "default" : "error"}
                                         onChange={newValue => {
                                 e.jsonpath = newValue;
                                 e.changed = true
                                 e.validationResult = undefined
                                 setExtractors([...extractors])
                                 if (e.validationTimer) {
                                    clearTimeout(e.validationTimer)
                                 }
                                 e.validationTimer = window.setTimeout(() => {
                                    if (e.jsonpath) {
                                        api.testJsonPath(e.jsonpath).then(result => {
                                            e.validationResult = result
                                            setExtractors([...extractors])
                                        })
                                    }
                                 }, 1000)
                              }}/>
                          </FormGroup>
                          { isTester &&
                          <Button variant="plain" style={{ position: "absolute", right: "0px", top: "22px" }}
                                  onClick={ () => {
                              e.deleted = true;
                              setExtractors([...extractors])
                          }}>
                             <OutlinedTimesCircleIcon style={{color: "#a30000"}} />
                          </Button>
                          }
                       </Form>
                    </>))}
                    { activeTab === 1 && isTester && <>

                       <Button
                          isDisabled={!uri}
                          onClick={() => {
                             if (uri) {
                               setExtractors([...extractors, { accessor: "", schema: uri }])
                             }
                          }} >Add extractor</Button>
                       { !uri && <><br /><span style={{ color: "red"}}>Please define an URI first.</span></> }
                    </> }
                </CardBody>
                { isTester &&
                <CardFooter>
                  <ActionGroup style={{ marginTop: 0 }}>
                      <Button variant="primary"
                          onClick={e => {
                              let savedSchema;
                              if (activeTab === 0) {
                                 savedSchema = editor.current?.getValue()
                              } else {
                                 savedSchema = editorSchema;
                              }
                              let newSchema = {
                                  id: schemaId !== "_new" ? parseInt(schemaId) : 0,
                                  name,
                                  uri: uri || "", // TODO require URI set?
                                  description,
                                  schema: savedSchema ? JSON.parse(savedSchema) : null,
                                  access,
                                  owner: owner || "__schema_created_by_user_without_role__", // TODO this shouldn't happen,
                                  token: null
                              }
                              actions.add(newSchema)(dispatch)
                                     .then(() => Promise.all(extractors.filter(e => e.changed || (e.deleted && e.accessor !== "")).map(e => api.addOrUpdateExtractor(e))))
                                     .then(() => setGoBack(true))
                                     .catch(e => {
                                        dispatch(alertAction("SAVE_SCHEMA", "Failed to save the schema", e, constraintValidationFormatter("the saved schema")))
                                     })
                          }}
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
