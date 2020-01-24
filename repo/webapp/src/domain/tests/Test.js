import React, { useState, useRef, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Button,
    Card,
    CardHeader,
    CardBody,
    Form,
    ActionGroup,
    FormGroup,
    TextArea,
    TextInput,
    Toolbar,    
    Tab,
    Tabs,
    ToolbarSection,
} from '@patternfly/react-core';
import { NavLink, Redirect } from 'react-router-dom';
import {
    OutlinedSaveIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import * as actions from './actions';
import * as selectors from './selectors';

import {
    accessName,
    defaultRoleSelector,
    isTesterSelector,
    roleToName
} from '../../auth.js'

//import Editor, {fromEditor} from '../../components/Editor';
import { fromEditor, toString } from '../../components/Editor';
import Editor from '../../components/Editor/monaco/Editor';
import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import OwnerSelect from '../../components/OwnerSelect'

const tabs = ["schema","view"]

export default () => {
    const { testId } = useParams();
    const test = useSelector(selectors.get(testId))
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [activeTab,setActiveTab] = useState(0)
    const [schema,setSchema] = useState(toString(test.schema) || "{}")
    const [view,setView] = useState(toString(test.view) || "[]")
    const [editorContent, setEditorContent] = useState(toString(test[tabs[activeTab]]) || "{}")
    const dispatch = useDispatch();
    useEffect(() => {
        if (testId !== "_new") {
            dispatch(actions.fetchTest(testId))
        }

    }, [dispatch, testId])
    useEffect(() => {
        setEditorContent(toString(test[tabs[activeTab]]) || "{}");//change the loaded document when the test changes
        setName(test.name);
        setDescription(test.description);
    }, [test,activeTab])
    const editor = useRef();
    const getFormTest = () => ({
        name,
        description,
        schema: fromEditor(editorContent),
        id: test.id
    })
    const isTester = useSelector(isTesterSelector)
    const defaultRole = useSelector(defaultRoleSelector)
    const [access, setAccess] = useState(0)
    const [owner, setOwner] = useState(defaultRole)
    const [goBack, setGoBack] = useState(false)
    return (
        // <PageSection>
        <React.Fragment>
            { goBack && <Redirect to="/test" /> }
            <Card style={{flexGrow:1}}>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                        <ToolbarSection aria-label="form">
                            <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
                                <FormGroup label="Name" isRequired={true} fieldId="name" helperText="names must be unique" helperTextInvalid="Name must be unique and not empty">
                                    <TextInput
                                        value={name}
                                        isRequired
                                        type="text"
                                        id="name"
                                        aria-describedby="name-helper"
                                        name="name"
                                        isReadOnly={!isTester}
                                        // isValid={name !== null && name.trim().length > 0}
                                        onChange={e => setName(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="Description" fieldId="description" helperText="" helperTextInvalid="">
                                    <TextArea
                                        value={description}
                                        type="text"
                                        id="description"
                                        aria-describedby="description-helper"
                                        name="description"
                                        readOnly={!isTester}
                                        isValid={true}
                                        onChange={e => setDescription(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="Owner" fieldId="testOwner">
                                   { isTester ? (
                                      <OwnerSelect includeGeneral={false}
                                                   selection={roleToName(owner)}
                                                   onSelect={selection => setOwner(selection.key)} />
                                   ) : (
                                      <TextInput value={roleToName(owner)} isReadOnly />
                                   )}
                                </FormGroup>
                                <FormGroup label="Access rights" fieldId="testAccess">
                                   { isTester ? (
                                      <AccessChoice checkedValue={access} onChange={setAccess} />
                                   ) : (
                                      <AccessIcon access={access} />
                                   )}
                                </FormGroup>
                                { isTester &&
                                <ActionGroup style={{ marginTop: 0 }}>
                                    <Button
                                        variant="primary"
                                        onClick={e => {
                                            const editorValue = fromEditor(editor.current.getValue())
                                            const newTest = {
                                                name,
                                                description,
                                                owner: owner,
                                                access: accessName(access),
                                                schema: fromEditor(schema),
                                                view: fromEditor(view)
                                            }
                                            newTest[tabs[activeTab]] = editorValue
                                            if (testId !== "_new") {
                                                newTest.id = testId;
                                            }
                                            
                                            dispatch(actions.sendTest(newTest))
                                            setGoBack(true)
                                        }}
                                    >Save</Button>
                                    <NavLink className="pf-c-button pf-m-secondary" to="/test/">
                                        Cancel
                                    </NavLink>
                                </ActionGroup>
                                }
                            </Form>
                        </ToolbarSection>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Tabs 
                        activeKey={activeTab} 
                        onSelect={(event,tabIndex)=>{
                            const editorValue = fromEditor(editor.current.getValue())
                            switch(activeTab){
                                case 0:
                                    setSchema(editorValue)
                                    break;
                                case 1:
                                    setView(editorValue)
                                    break;
                                default:
                                    console.log("unknown activeTab",activeTab)
                            }
                            setActiveTab(tabIndex)}
                        }
                    >
                        {tabs.map((tab,tabIndex)=>(
                            <Tab key={tabIndex} eventKey={tabIndex} title={tab}>
                            </Tab>
                        ))}
                    </Tabs>
                    <Editor
                        value={editorContent}
                        setValueGetter={e => { console.log("setValueGetter", e); editor.current = e }}
                        onChange={e => { setEditorContent(e) }}
                        options={{ mode: "application/ld+json" }}
                    />
                </CardBody>
            </Card>
        </React.Fragment>
        // </PageSection>        
    )
}