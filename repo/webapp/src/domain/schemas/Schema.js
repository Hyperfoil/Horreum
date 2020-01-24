import React, { useState, useRef, useMemo, useEffect } from 'react';
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
    InputGroup,
    InputGroupText,
    PageSection,
    TextArea,
    TextInput,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    ToolbarSection,
} from '@patternfly/react-core';
import { NavLink, Redirect } from 'react-router-dom';
import {
    EditIcon,
    OutlinedSaveIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import * as actions from './actions';
import * as selectors from './selectors';
import { accessName, isTesterSelector, defaultRoleSelector, roleToName } from '../../auth.js'

import { fromEditor, toString } from '../../components/Editor';
import Editor from '../../components/Editor/monaco/Editor';
import AccessIcon from '../../components/AccessIcon'
import AccessChoice from '../../components/AccessChoice'
import OwnerSelect from '../../components/OwnerSelect'

export default () => {
    const { schemaId } = useParams();
    console.log("schemaId",schemaId)
    const schema = useSelector(selectors.getById(schemaId))
    const [name, setName] = useState("")
    const [description, setDescription] = useState("");
    const [json, setJson] = useState(toString(schema.schema) || "{}")
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
        setJson(toString(schema.schema) || "{}")
    }, [schema])
    const editor = useRef();
    const getFormSchema = () => {
        const rtrn = {
            name,
            description,
            schema: fromEditor(json),
        }
        if (schemaId !== "_new") {
            rtrn.id = schemaId;
        }
        return rtrn;
    }
    const isTester = useSelector(isTesterSelector)
    const defaultRole = useSelector(defaultRoleSelector)
    const [access, setAccess] = useState(0)
    const [owner, setOwner] = useState(defaultRole)
    const [goBack, setGoBack] = useState(false)
    return (
        <React.Fragment>
            { goBack && <Redirect to='/schema' /> }
            <Card style={{ flexGrow: 1 }}>
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
                                        isReadOnly={ !isTester }
                                        onChange={e => setName(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="Description" fieldId="schemaDescription" helperText="" helperTextInvalid="">
                                    <TextArea
                                        value={description}
                                        type="text"
                                        id="schemaDescription"
                                        aria-describedby="description-helper"
                                        name="schemaDescription"
                                        readOnly={ !isTester }
                                        isValid={true}
                                        onChange={e => setDescription(e)}
                                    />
                                </FormGroup>
                                <FormGroup label="Owner" fieldId="schemaOwner">
                                   { isTester ? (
                                      <OwnerSelect includeGeneral={false}
                                                   selection={roleToName(owner)}
                                                   onSelect={selection => setOwner(selection.key)} />
                                   ) : (
                                      <TextInput value={roleToName(owner)} isReadOnly />
                                   )}
                                </FormGroup>
                                <FormGroup label="Access rights" fieldId="schemaAccess">
                                   { isTester ? (
                                      <AccessChoice checkedValue={access} onChange={setAccess} />
                                   ) : (
                                      <AccessIcon access={access} />
                                   )}
                                </FormGroup>
                                { isTester && <>
                                <ActionGroup style={{ marginTop: 0 }}>
                                    <Button variant="primary" 
                                        onClick={e => {
                                            const editorValue = fromEditor(editor.current.getValue())
                                            const newSchema = {
                                                name,
                                                description,
                                                access: accessName(access),
                                                owner,
                                                schema: editorValue
                                            }
                                            if (schemaId !== "_new"){
                                                newSchema.id = schemaId;
                                            }
                                            dispatch(actions.add(newSchema))
                                            setGoBack(true)
                                        }}
                                    >Save</Button>
                                    <NavLink className="pf-c-button pf-m-secondary" to="/schema/">
                                        Cancel
                                    </NavLink>
                                </ActionGroup>
                                </>}
                            </Form>
                        </ToolbarSection>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Editor
                        value={json}
                        setValueGetter={e => { editor.current = e }}
                        onChange={e => { setJson(e) }}
                        options={{ mode: "application/ld+json" }}
                    />
                </CardBody>
            </Card>
        </React.Fragment>
    )
}


