import React, { useState, useMemo, useEffect } from 'react';
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
import {
    EditIcon,
    OutlinedSaveIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import * as actions from './actions';
import * as selectors from './selectors';

import Editor, {fromEditor} from '../../components/Editor';
export default () => {
    const { testId } = useParams();
    const test = useSelector(selectors.get(testId))
    const [name,setName] = useState("");
    const [description,setDescription] = useState("");
    const [schema, setSchema] = useState(test.schema || {})
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(actions.fetchTest(testId))
    }, [dispatch, testId])
    useEffect(() => {
        setSchema(test.schema || {});//change the loaded document when the test changes
        setName(test.name);
        setDescription(test.description);
    }, [test])

    const getFormTest = ()=>({
        name,
        description,
        schema: fromEditor(schema),
        id: test.id
    })
    return (
        // <PageSection>
        <React.Fragment>
            <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between", backgroundColor: '#f7f7f7', borderBottom: '1px solid #ddd' }}>
                <ToolbarSection aria-label="form">
                    <Form isHorizontal={true} style={{gridGap:"2px",width:"100%",paddingRight:"8px"}}>
                        <FormGroup label="Name" isRequired={true} fieldId="name" helperText="Test Name" helperTextInvalid="Name must be unique and not empty">
                            <TextInput
                                value={name}
                                isRequired
                                type="text"
                                id="name"
                                aria-describedby="name-helper"
                                name="name"
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
                                isValid={true}
                                onChange={e => setDescription(e)}
                            />
                        </FormGroup>
                        <ActionGroup style={{marginTop:0}}>
                            <Button variant="primary" onClick={e => { }}><OutlinedSaveIcon /> save</Button>
                            <Button variant="secondary" onClick={e => { }}><OutlinedSaveIcon /> cancel</Button>
                        </ActionGroup>
                    </Form>
                </ToolbarSection>
            </Toolbar>
            <Editor value={schema} onChange={e => { setSchema(e) }} options={{mode: "application/ld+json"}}/>
        </React.Fragment>
        // </PageSection>        
    )
}