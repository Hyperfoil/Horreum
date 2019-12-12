import React, { useState, useRef, useMemo, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import { DateTime, Duration } from 'luxon';
import jsonpath from 'jsonpath';

import * as actions from './actions';
import * as selectors from './selectors';


import Editor from '../../components/Editor';
import {
    Button,
    ButtonVariant,
    Card,
    CardHeader,
    CardBody,
    Dropdown,
    DropdownToggle,
    DropdownItem,
    DropdownSeparator,
    InputGroup,
    InputGroupText,
    PageSection,
    TextInput,
    Title,
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
import { NavLink } from 'react-router-dom';

export default () => {
    const { id } = useParams();
    const inputEl = useRef(null);
    const run = useSelector(selectors.get(id));
    const [data, setData] = useState(run.data)
    const [invalidPath, setInvalidPath] = useState(false)
    const [pathType,setPathType] = useState('query')
    const [pathTypeOpen,setPathTypeOpen] = useState(false)
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(actions.get(id))
    }, [dispatch, id])
    useEffect(() => {
        setData(run.data || {});//change the loaded document when the run changes
    }, [run])
    return (
        // <PageSection>
        <React.Fragment>
            <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between", backgroundColor: '#f7f7f7', borderBottom: '1px solid #ddd' }}>
                <ToolbarSection aria-label="info">
                    <table className="pf-c-table pf-m-compact">
                        <tbody>
                            <tr>
                                <th>id</th>
                                <th>test</th>
                                <th>start</th>
                                <th>stop</th>
                            </tr>
                            <tr>
                                <td>{run.id}</td>
                                <td><NavLink to={`/test/${run.testId}`} >{run.testId}</NavLink></td>
                                <td>{run.start ? DateTime.fromMillis(run.start).toFormat("yyyy-LL-dd HH:mm:ss ZZZ") : "--"}</td>
                                <td>{run.stop ? DateTime.fromMillis(run.stop).toFormat("yyyy-LL-dd HH:mm:ss ZZZ") : "--"}</td>
                            </tr>
                        </tbody>
                    </table>
                </ToolbarSection>
                <ToolbarSection aria-label="search" style={{ marginTop: 0 }}>
                    <InputGroup>
                        <InputGroupText>jsonpath</InputGroupText>
                        {/* <Dropdown
                            isOpen={pathTypeOpen} 
                            onSelect={(e,f)=>{ setPathTypeOpen(false); }}
                            toggle={
                                <DropdownToggle onToggle={e=>{ setPathTypeOpen(e); }}>type</DropdownToggle>
                            }
                            dropdownItems={
                            [
                                <DropdownItem key="query">jQ.query</DropdownItem>,
                                <DropdownItem key="sql">sql</DropdownItem>,
                                <DropdownItem key="jsonb_path_query_first">jsonb_first</DropdownItem>,
                                <DropdownItem key="jsonb_path_query_array">jsonb_array</DropdownItem>,
                            ]}
                        >

                        </Dropdown> */}
                        <input
                            className="pf-c-form-control"
                            aria-label="jsonpath"
                            aria-invalid={invalidPath}
                            ref={inputEl}
                            title={invalidPath ? data : "jsonpath"}
                        />
                        <Button variant={ButtonVariant.control} onClick={
                            e => {
                                const pathQuery = inputEl.current.value;
                                if (pathQuery === "") {
                                    setInvalidPath(false);
                                    setData(run.data);
                                } else {
                                    try {
                                        const found = jsonpath.query(run.data, pathQuery)
                                        setInvalidPath(false)
                                        setData(found)
                                    } catch (e) {
                                        setInvalidPath(true)
                                        setData(e.message)
                                    }
                                }

                            }
                        }>Find</Button>
                    </InputGroup>
                </ToolbarSection>
            </Toolbar>
            <Editor value={data} onChange={e => { setData(e) }} options={{ readOnly: true }} />
        </React.Fragment>
        // </PageSection>        
    )
}