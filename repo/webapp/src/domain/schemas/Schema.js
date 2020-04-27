import React, { useState, useRef, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
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
    ToolbarSection,
    Tooltip,
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
import { accessName, isTesterSelector, defaultRoleSelector, roleToName } from '../../auth.js'

import { fromEditor, toString } from '../../components/Editor';
import Editor from '../../components/Editor/monaco/Editor';
import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import OwnerSelect from '../../components/OwnerSelect'

export default () => {
    const { schemaId } = useParams();
    const schema = useSelector(selectors.getById(schemaId))
    const [name, setName] = useState("")
    const [description, setDescription] = useState("");
    const [testPath, setTestPath] = useState(schema.testPath || "")
    const [startPath, setStartPath] = useState(schema.startPath || "")
    const [stopPath, setStopPath] = useState(schema.stopPath || "")
    const [editorSchema, setEditorSchema] = useState(toString(schema.schema) || "{}")

    const dispatch = useDispatch();
    useEffect(() => {
        if (schemaId !== "_new") {
            dispatch(actions.getById(schemaId))
        }
    }, [dispatch, schemaId])
    useEffect(() => {
        console.log("userEffect",schema)
        setName(schema.name || "");
        setDescription(schema.description || "")
        setUri(schema.uri || "")
        setTestPath(schema.testPath)
        setStartPath(schema.startPath)
        setStopPath(schema.stopPath)
        setOwner(schema.owner)
        setAccess(schema.access)
        setEditorSchema(toString(schema.schema) || "{}")
    }, [schema])
    const editor = useRef();
    const [uri, setUri] = useState(schema.uri)
    const [uriMatching, setUriMatching] = useState(true)
    const [importFailed, setImportFailed] = useState(false)
    // TODO: use this in reaction to editor change
    const parseUri = newSchema => {
        const schemaUri = jsonpath.value(JSON.parse(newSchema), "$['$id']")
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
    const checkUri = newUri => {
        const currentSchema = editor.current.getValue()
        const schemaUri = jsonpath.value(JSON.parse(currentSchema), "$['$id']")
        if (!schemaUri || schemaUri === "") {
           return // nothing to do
        } else {
           setUriMatching(newUri === schemaUri)
        }
    }

    const isTester = useSelector(isTesterSelector)
    const defaultRole = useSelector(defaultRoleSelector)
    const [access, setAccess] = useState(0)
    const [owner, setOwner] = useState(defaultRole)
    const [goBack, setGoBack] = useState(false)
    const [saveFailed, setSaveFailed] = useState(false)

    const [activeTab, setActiveTab] = useState(0)
    const [extractors, setExtractors] = useState([])
    useEffect(() => {
        api.listExtractors(schemaId).then(result => setExtractors(result.map(e => {
            e.newName = e.accessor
            return e
        }).sort((a, b) => a.accessor.localeCompare(b.accessor))))
    }, [schemaId])
    return (
        <React.Fragment>
            { goBack && <Redirect to='/schema' /> }
            <Card style={{ flexGrow: 1 }}>
                { !schema && (<center><Spinner /></center>) }
                { schema && (<>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                        <ToolbarSection aria-label="form">
                            <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
                                <FormGroup label="Name" isRequired={true} fieldId="schemaName" helperText="names must be unique" helperTextInvalid="Name must be unique and not empty">
                                    <TextInput
                                        value={name}
                                        isRequired
                                        type="text"
                                        id="schemaName"
                                        aria-describedby="name-helper"
                                        name="schemaName"
                                        isValid={ (name && name !== "") || !isTester }
                                        isReadOnly={ !isTester }
                                        onChange={e => setName(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="URI" isRequired={true} fieldId="schemaURI" helperTextInvalid="Must provide a valid URI">
                                   <>
                                   <div style={{ display: "flex" }}>
                                   { (!uri || uri === "") && isTester &&
                                   <Tooltip content={"Import URI from the schema"}>
                                      <Button variant="control"
                                              style={{ float: "left" }}
                                              onClick={ () => parseUri(editor.current.getValue()) }
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
                                        isValid={(uri && uri !== "") || !isTester}
                                        onChange={e => {
                                            setUri(e)
                                            checkUri(e)
                                        }}
                                        placeholder={ isTester ? "Click button to import" : "" }
                                        style={{ width: "100%" }}
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
                                <FormGroup label="Test name JSON path" fieldId="testPath">
                                    <TextInput id="testPath"
                                               value={testPath || ""}
                                               onChange={setTestPath}
                                               placeholder="e.g. $.testName"
                                               isReadOnly={ !isTester } />
                                </FormGroup>
                                <FormGroup label="Start time JSON path" fieldId="startPath">
                                    <TextInput id="startPath"
                                               value={startPath || ""}
                                               onChange={setStartPath}
                                               placeholder="e.g. $.startTimestamp"
                                               isReadOnly={ !isTester } />
                                </FormGroup>
                                <FormGroup label="Stop time JSON path" fieldId="stopPath">
                                    <TextInput id="stopPath"
                                               value={stopPath || ""}
                                               onChange={setStopPath}
                                               placeholder="e.g. $.stopTimestamp"
                                               isReadOnly={ !isTester } />
                                </FormGroup>
                                <FormGroup label="Owner" fieldId="schemaOwner">
                                   { isTester ? (
                                      <OwnerSelect includeGeneral={false}
                                                   selection={roleToName(owner)}
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
                        </ToolbarSection>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Tabs>
                       <Tab key="schema" eventKey={0} title="JSON schema" style={{ height: "100%" }} onClick={ () => setActiveTab(0) }/>
                       <Tab key="extractors" eventKey={1} title="Schema extractors" onClick={ () => setActiveTab(1) }/>
                    </Tabs>
                    { activeTab === 0 &&
                    <div style={{ height: "600px" }}>
                       <Editor
                         value={editorSchema || "{}"}
                         setValueGetter={e => { editor.current = e }}
                         options={{ mode: "application/ld+json" }}
                       />
                    </div>
                    }
                    { activeTab === 1 && extractors.filter(e => !e.deleted).map(e => (<>
                       <Form isHorizontal={true} style={{ gridGap: "2px", marginBottom: "10px", paddingRight: "40px", position: "relative" }}>
                          <FormGroup label="Accessor">
                              <TextInput value={e.newName}
                                         isReadOnly={!isTester}
                                         onChange={newValue => {
                                 e.newName = newValue
                                 e.changed = true
                                 setExtractors([...extractors])
                              }}/>
                          </FormGroup>
                          <FormGroup label="JSON path"
                                     isValid={!e.jsonpath || !e.jsonpath.trim().startsWith("$")}
                                     helperTextInvalid="JSON path must not start with '$'">
                              <TextInput value={e.jsonpath}
                                         isReadOnly={!isTester}
                                         isValid={!e.jsonpath || !e.jsonpath.trim().startsWith("$")}
                                         onChange={newValue => {
                                 e.jsonpath = newValue;
                                 e.changed = true
                                 setExtractors([...extractors])
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
                    { activeTab === 1 && isTester &&
                       <Button onClick={() => setExtractors([...extractors, { schema: uri }])}>Add extractor</Button>
                    }
                </CardBody>
                { isTester &&
                <CardFooter>
                  { saveFailed &&
                     <Alert variant="warning" title="Failed to save the schema" />
                  }
                  <ActionGroup style={{ marginTop: 0 }}>
                      <Button variant="primary"
                          onClick={e => {
                              // const editorValue = fromEditor(editor.current.getValue())
                              const newSchema = {
                                  name,
                                  uri,
                                  description,
                                  schema: fromEditor(editor.current.getValue()),
                                  testPath: testPath,
                                  startPath: startPath,
                                  stopPath: stopPath,
                                  access: accessName(access),
                                  owner,
                              }
                              if (schemaId !== "_new"){
                                  newSchema.id = schemaId;
                              }
                              actions.add(newSchema)(dispatch)
                                     .then(() => Promise.all(extractors.filter(e => e.changed || e.deleted).map(e => api.addOrUpdateExtractor(e))))
                                     .then(() => setGoBack(true))
                                     .catch(() => {
                                        setSaveFailed(true)
                                        setInterval(() => setSaveFailed(false), 5000)
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


