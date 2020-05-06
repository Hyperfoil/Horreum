import React, { useState, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Button,
    Card,
    CardHeader,
    CardBody,
    CardFooter,
    Checkbox,
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
} from '@patternfly/react-core';
import { useHistory } from 'react-router-dom';
import {
    ArrowAltCircleDownIcon,
    ArrowAltCircleUpIcon,
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

import { ADD_ALERT, defaultFormatError } from "../../alerts"

import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import Accessors from '../../components/Accessors'
import OwnerSelect from '../../components/OwnerSelect'

export default () => {
    const { testId } = useParams();
    const test = useSelector(selectors.get(testId))
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const dispatch = useDispatch();
    useEffect(() => {
        if (testId !== "_new") {
            dispatch(actions.fetchTest(testId))
        }
    }, [dispatch, testId])
    useEffect(() => {
        if (!test) {
            return
        }
        setName(test.name);
        document.title = (testId === "_new" ? "New test" : test && test.name ? test.name : "Loading test...") + " | Horreum"
        setDescription(test.description);
        if (test.defaultView) {
            setView(test.defaultView)
        }
    }, [test])
    const isTester = useSelector(isTesterSelector)
    const defaultRole = useSelector(defaultRoleSelector)
    useEffect(() => {
      setOwner(defaultRole)
    }, [defaultRole])
    const [access, setAccess] = useState(0)
    const [owner, setOwner] = useState(defaultRole)
    const [view, setView] = useState({ name: "default", components: []})
    const history = useHistory()
    return (
        // <PageSection>
        <React.Fragment>
            <Card style={{flexGrow:1}}>
                { !test && (<center><Spinner /></center>) }
                { test && (<>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                        <ToolbarSection aria-label="form">
                            <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
                                <FormGroup label="Name" isRequired={true} fieldId="name" helperText="names must be unique" helperTextInvalid="Name must be unique and not empty">
                                    <TextInput
                                        value={name || ""}
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
                                        value={description || ""}
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
                                      <TextInput value={roleToName(owner) || ""} id="testOwner" isReadOnly />
                                   )}
                                </FormGroup>
                                <FormGroup label="Access rights" fieldId="testAccess">
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
                    { /* TODO: display more than default view */ }
                    <Tabs>
                        <Tab key="__default" eventKey={0} title="Default view"></Tab>
                        <Tab key="__new" eventKey={1} title="+"></Tab>
                    </Tabs>
                    { (!view.components || view.components.length === 0) && "The view is not defined" }
                    { view.components && view.components.map((c, i) => (
                        <div style={{ display: "flex "}}>
                           <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", float: "left", marginBottom: "25px" }}>
                               <FormGroup label="Header" fieldId="header">
                                 <TextInput value={ c.headerName || "" } placeholder="e.g. 'Run duration'"
                                            id="header"
                                            onChange={ value => { c.headerName = value; setView({ ...view}) }}
                                            isValid={ !!c.headerName && c.headerName.trim() !== "" }
                                            isReadOnly={!isTester} />
                               </FormGroup>
                               <FormGroup label="Accessors" fieldId="accessor">
                                 <Accessors id="accessor"
                                            value={ (c.accessors && c.accessors.split(/[,;] */).map(a => a.trim()).filter(a => a.length != 0)) || [] }
                                            onChange={ value => { c.accessors = value.join(";"); setView({ ...view }) }}
                                            isReadOnly={!isTester} />
                               </FormGroup>
                               <FormGroup label="Rendering" fieldId="rendering">
                                 <TextArea value={ (c.render && c.render.toString()) || "" }
                                           id="rendering"
                                           onChange={ value => { c.render = value; setView({ ...view }) }}
                                           readOnly={!isTester}/>
                               </FormGroup>
                           </Form>
                           { isTester &&
                           <div style={{ width: "40px", float: "right", display: "table-cell", position: "relative", marginBottom: "25px" }}>
                               <Button style={{width: "100%", marginTop: "4px"}}
                                       variant="plain"
                                       isDisabled={ i === 0 }
                                       onClick={ () => {
                                          let prev = view.components[i - 1]
                                          view.components[i - 1] = c;
                                          view.components[i] = prev;
                                          c.headerOrder = i - 1;
                                          prev.headerOrder = i;
                                          setView({ ...view })
                               }} ><ArrowAltCircleUpIcon /></Button>
                               <Button style={{width: "100%", position: "absolute", left: "0px", top: "38%"}}
                                       variant="plain"
                                       onClick={ () => {
                                          view.components.splice(i, 1)
                                          view.components.forEach((c, i) => c.headerOrder = i)
                                          setView({ ...view})
                               }}><OutlinedTimesCircleIcon style={{color: "#a30000"}}/></Button>
                               <Button style={{width: "100%", position: "absolute", left: "0px", bottom: "4px"}}
                                       variant="plain"
                                       isDisabled={ i === view.components.length - 1 }
                                       onClick={ () => {
                                          let prev = view.components[i + 1]
                                          view.components[i + 1] = c;
                                          view.components[i] = prev;
                                          c.headerOrder = i + 1;
                                          prev.headerOrder = i;
                                          setView({ ...view})
                               }} ><ArrowAltCircleDownIcon /></Button>
                           </div>
                           }
                        </div>
                    ))}
                    { isTester &&
                    <ActionGroup>
                        <Button onClick={ () => {
                           const components = view.components || []
                           setView({ ...view, components: [ ...components, { isArray: false, headerOrder: components.length} ] })
                        }} >Add component</Button>

                    </ActionGroup>
                    }
                </CardBody>
                { isTester &&
                <CardFooter>
                   <ActionGroup style={{ marginTop: 0 }}>
                       <Button
                           variant="primary"
                           onClick={e => {
                               const newTest = {
                                   name,
                                   description,
                                   defaultView: view,
                                   owner: owner,
                                   access: accessName(access),
                               }
                               if (testId !== "_new") {
                                   newTest.id = testId;
                               }

                               dispatch(actions.sendTest(newTest)).then(() => history.goBack(), e => {
                                  dispatch({
                                     type: ADD_ALERT,
                                     alert: {
                                        type: "TEST_UPDATE_FAILED",
                                        title: "Test update failed",
                                        content: defaultFormatError(e),
                                     }
                                  })
                               })
                           }}
                       >Save</Button>
                       <Button className="pf-c-button pf-m-secondary" onClick={() => history.goBack()}>
                           Cancel
                       </Button>
                   </ActionGroup>
                </CardFooter>
                }
                </>)}
            </Card>
        </React.Fragment>
        // </PageSection>        
    )
}