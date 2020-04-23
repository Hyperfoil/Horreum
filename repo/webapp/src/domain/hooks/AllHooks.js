import React, { useMemo, useEffect, useState } from 'react';

import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Button,
    Card,
    CardHeader,
    CardBody,
    PageSection,
    Toolbar,
    ToolbarSection,
} from '@patternfly/react-core';
import {
    OutlinedTimesCircleIcon,
    PlusIcon,
} from '@patternfly/react-icons';

import {all,add, remove} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';
import AddHookModal from './AddHookModal';


export default ()=>{
    document.title = "WebHooks | Horreum"
    const dispatch = useDispatch();
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

    ],[dispatch])
    const [isOpen,setOpen] = useState(false);
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
                    <Button variant="primary" onClick={e=>{ setOpen(true); }}><PlusIcon/> Add Hook</Button>
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