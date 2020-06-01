import React, { useMemo, useEffect } from 'react';

import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Card,
    CardHeader,
    CardBody,
    PageSection,
} from '@patternfly/react-core';
import { NavLink } from 'react-router-dom';

import {
   FolderOpenIcon
} from '@patternfly/react-icons'

import {
   fetchSummary,
   resetToken,
   dropToken,
   updateAccess,
   deleteTest,
} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';
import AccessIcon from '../../components/AccessIcon';
import ActionMenu from '../../components/ActionMenu';

import { isTesterSelector, registerAfterLogin, roleToName } from '../../auth'
import { alertAction } from '../../alerts'

export default ()=>{
    document.title = "Tests | Horreum"
    const dispatch = useDispatch();
    const columns = useMemo(()=>[
        {
          Header:"Id",accessor:"id",
          Cell: (arg) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/test/${value}`}>{value}</NavLink>)
          }
        },
        {
          Header: "Access", accessor:"access",
          Cell: (arg) => <AccessIcon access={arg.cell.value} />
        },
        {Header:"Owner",accessor:"owner", Cell: (arg) => roleToName(arg.cell.value)},
        {Header:"Name",accessor:"name", Cell: (arg) => (<NavLink to={`/test/${arg.row.original.id}`}>{ arg.cell.value }</NavLink>)},
        {Header:"Description",accessor:"description"},
        {
          Header:"Run Count",accessor:"count",
          Cell: (arg) => {
            const {cell: {value, row: {index}}, data} = arg;
            return (<NavLink to={`/run/list/${data[index].id}`}>{value}&nbsp;<FolderOpenIcon /></NavLink>)
          }
        },
        {
          Header:"Actions",
          id:"actions",
          accessor: "id",
          Cell: (arg) => {
            return (
            <ActionMenu id={arg.cell.value}
                        access={arg.row.original.access}
                        owner={arg.row.original.owner}
                        token={arg.row.original.token}
                        tokenToLink={ (id, token) => "/test/" + id + "?token=" + token }
                        onTokenReset={ id => dispatch(resetToken(id)) }
                        onTokenDrop={ id => dispatch(dropToken(id)) }
                        onAccessUpdate={ (id, owner, access) => dispatch(updateAccess(id, owner, access)) }
                        description={ "test " + arg.row.original.name }
                        onDelete={ id => dispatch(deleteTest(id)).catch(e => dispatch(alertAction("DELETE_TEST", "Failed to delete test", e))) }/>
            )
          }
        }
    ], [dispatch])
    const allRuns = useSelector(selectors.all);
    useEffect(()=>{
        dispatch(fetchSummary())
        dispatch(registerAfterLogin("reload_tests", () => {
          dispatch(fetchSummary())
        }))
    },[dispatch])
    const isTester = useSelector(isTesterSelector)
    const isLoading = useSelector(selectors.isLoading)
    return (
        <PageSection>
          <Card>
            { isTester &&
            <CardHeader>
              <NavLink className="pf-c-button pf-m-primary" to="/test/_new">
                New Test
              </NavLink>
            </CardHeader>
            }
            <CardBody>
              <Table columns={columns} data={allRuns} isLoading={isLoading}/>
            </CardBody>
          </Card>
        </PageSection>
    )
}
