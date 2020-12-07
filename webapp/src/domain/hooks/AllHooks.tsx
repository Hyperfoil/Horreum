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
    ToolbarContent,
    ToolbarItem, Alert,
} from '@patternfly/react-core';
import {
    OutlinedTimesCircleIcon,
    PlusIcon,
} from '@patternfly/react-icons';

import {all,add, remove} from './actions';
import * as selectors from './selectors';
import { isAdminSelector } from '../../auth'

import { all as allTests } from '../tests/selectors'
import { fetchSummary } from '../tests/actions'

import Table from '../../components/Table';
import AddHookModal from './AddHookModal';
import { Column } from 'react-table';
import { Hook } from './reducers';

export const globalEventTypes = ["test/new","run/new","change/new"]
export const testHookEventTypes = ["run/new","change/new"]

export default ()=>{
    document.title = "WebHooks | Horreum"
    const dispatch = useDispatch();
    const tests = useSelector(allTests)
    useEffect(() => {
        dispatch(fetchSummary())
    }, [dispatch])
    const columns: Column<Hook>[] = useMemo(()=>[
        {
            Header:"Url",accessor:"url"
        },
        {
            Header:"Event type",accessor:"type"
        },
        {
            Header:"Target",accessor:"target",
            Cell: (arg: any) => {
                if (arg.row.original.type === "run/new" && tests) {
                    if (arg.cell.value === -1) {
                        return "All tests"
                    }
                    const test = tests.find(t => t.id == arg.cell.value)
                    return test ? test.name : "Unknown test";
                } else {
                    return "";
                }
            }
        },
        {
            Header:"Active",accessor:"active"
        },
        {
            Header:"",accessor:"id",disableSortBy:true,
            Cell: (arg: any)=>{
                const {cell: {value} } = arg;
                return (<>
                    <Button variant="link" style={{color: "#a30000"}} onClick={e=>{dispatch(remove(value))}}><OutlinedTimesCircleIcon/></Button>
                </>)
            }
        }

    ],[dispatch, tests])
    const [isOpen,setOpen] = useState(false);
    const list = useSelector(selectors.all);
    const isAdmin = useSelector(isAdminSelector)
    useEffect(()=>{
        if (isAdmin) {
            dispatch(all())
        }
    },[dispatch, isAdmin])
    return (
        <PageSection>
            <Alert variant="info" title="These Webhooks are global webhooks.  For individual test webhooks, please configure in the Test definition" />
          <AddHookModal isOpen={isOpen} onCancel={()=>setOpen(false)} onSubmit={(v)=>{setOpen(false); dispatch(add(v)); }} />
          <Card>
            <CardHeader>
                <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between"}}>
                    <ToolbarContent>
                        <ToolbarItem aria-label="info">
                            <Button variant="primary" onClick={e=>{ setOpen(true); }}><PlusIcon/> Add Hook</Button>
                        </ToolbarItem>
                    </ToolbarContent>
                </Toolbar>
            </CardHeader>
            <CardBody>
              <Table columns={columns} data={list || []} />
            </CardBody>
          </Card>
        </PageSection>
    )
}