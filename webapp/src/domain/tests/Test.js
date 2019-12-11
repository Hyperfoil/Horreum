import React, { useState, useMemo, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Button,
    Card,
    CardHeader,
    CardBody,
    InputGroup,
    InputGroupText,
    PageSection,
    TextInput,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    ToolbarSection

} from '@patternfly/react-core';
import {
    EditIcon,
    OutlinedSaveIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import * as actions from './actions';
import * as selectors from './selectors';

import Editor from '../../components/Editor';
export default () => {
    const { testId } = useParams();
    const test = useSelector(selectors.get(testId))
    const [schema, setSchema] = useState(test.schema || {})
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(actions.fetchTest(testId))
    }, [dispatch, testId])
    useEffect(() => {
        setSchema(test.schema || {});//change the loaded document when the test changes
    }, [test])

    return (
        // <PageSection>
        <React.Fragment>
            <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between", backgroundColor: '#f7f7f7', borderBottom: '1px solid #ddd' }}>
                <ToolbarGroup style={{flexGrow:1}}>
                    <ToolbarItem style={{flexGrow:1,padding:5}}>
                        <InputGroup>
                            <InputGroupText>Name</InputGroupText>
                            <TextInput value={test.name} />
                        </InputGroup>
                        <InputGroup>
                            <InputGroupText>Description</InputGroupText>
                            <TextInput value={test.description} />
                        </InputGroup>
                    </ToolbarItem>
                </ToolbarGroup>
                <ToolbarGroup>
                    <ToolbarItem>
                        <Button variant="control" onClick={e=>{}}><OutlinedSaveIcon/> save</Button>
                    </ToolbarItem>
                </ToolbarGroup>
            </Toolbar>
            <Editor value={schema} onChange={e => { setSchema(e) }} />
        </React.Fragment>
        // </PageSection>        
    )
}