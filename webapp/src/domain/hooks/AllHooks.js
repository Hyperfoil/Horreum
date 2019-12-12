import React, { useMemo, useEffect, useState } from 'react';

import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Button,
    ButtonVariant,
    Card,
    CardHeader,
    CardBody,
    PageSection,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    ToolbarSection,
} from '@patternfly/react-core';
import {
    EditIcon,
    OutlinedSaveIcon,
    OutlinedTimesCircleIcon,
    PlusIcon,
} from '@patternfly/react-icons';
import { DateTime, Duration } from 'luxon';
import { NavLink } from 'react-router-dom';

import {all,add, remove} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';
import AddHookModal from './AddHookModal';


export default ()=>{
    const columns = useMemo(()=>[
        {
            Header:"Url",accessor:"url"
        },
        {
            Header:"Event type",accessor:"type"
        },
        {
            Header:"Target",accessor:"target"
        },
        {
            Header:"Active",accessor:"active"
        },
        {
            Header:"",accessor:"id",disableSortBy:true,
            Cell: (arg)=>{
                const {cell: {value} } = arg;
                return (<>
                    <Button variant="link" style={{color: "#a30000"}} onClick={e=>{dispatch(remove(value))}}><OutlinedTimesCircleIcon/></Button>
                </>)
            }
        }

    ],[])
    const [isOpen,setOpen] = useState(false);
    const dispatch = useDispatch();
    const list = useSelector(selectors.all);
    useEffect(()=>{
        dispatch(all())
    },[dispatch])
    return (
        <PageSection>
          <AddHookModal isOpen={isOpen} onCancel={()=>setOpen(false)} onSubmit={(v)=>{setOpen(false); dispatch(add(v)); }} />
          <Card>
            <CardHeader>
                <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between"}}>
                    <ToolbarSection aria-label="info">
                    <Button variant="link" style={{color: "#004080"}} onClick={e=>{ setOpen(true); }}><PlusIcon/> Add Hook</Button>
                    </ToolbarSection>
                </Toolbar>
            </CardHeader>
            <CardBody>
              <Table columns={columns} data={list} />
            </CardBody>
          </Card>
        </PageSection>
    )
}