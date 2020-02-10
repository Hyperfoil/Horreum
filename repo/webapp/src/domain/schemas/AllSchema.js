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
import { NavLink } from 'react-router-dom';
import {
    EditIcon,
    OutlinedSaveIcon,
    OutlinedTimesCircleIcon,
    PlusIcon,
} from '@patternfly/react-icons';

import * as actions from './actions';
import * as selectors from './selectors';
import { isTesterSelector, registerAfterLogin, roleToName } from '../../auth.js'
import Table from '../../components/Table';
import AccessIcon from '../../components/AccessIcon';
import ActionMenu from '../../components/ActionMenu';

export default () => {
    const columns = useMemo(() => [
        {
            Header: "Access", accessor:"access",
            Cell: (arg) => <AccessIcon access={arg.cell.value} />
        },
        {
            Header: "Owner", accessor:"owner",
            Cell: (arg) => roleToName(arg.cell.value)},
        {
            Header: "Name", accessor: "name",
            Cell: (arg) => { return (
               <NavLink to={ "/schema/" + arg.row.original.id } >{ arg.cell.value }</NavLink>
            )}
        },
        {
            Header: "URI", accessor: "uri"
        },
        {
            Header: "Description", accessor: "description"
        },
        {
            Header:"Actions",
            accessor: "id",
            Cell: (arg) => {
                return (
                    <ActionMenu id={arg.cell.value}
                        owner={ arg.row.original.owner }
                        access={ arg.row.original.access }
                        token={ arg.row.original.token }
                        tokenToLink={ (id, token) => "/schema/" + id + "?token=" + token }
                        onTokenReset={ id => dispatch(actions.resetToken(id)) }
                        onTokenDrop={ id => dispatch(actions.dropToken(id)) }
                        onAccessUpdate={ (id, owner, access) => dispatch(actions.updateAccess(id, owner, access)) } />
                )
            }
        }
    ], [])
    const dispatch = useDispatch();
    const list = useSelector(selectors.all);
    useEffect(() => {
        dispatch(actions.all())
        dispatch(registerAfterLogin("reload_schemas", () => {
           dispatch(actions.all())
        }))
    }, [dispatch])
    const isTester = useSelector(isTesterSelector)
    return (
        <PageSection>
            <Card>
                { isTester &&
                <CardHeader>
                    <NavLink className="pf-c-button pf-m-primary" to="/schema/_new">
                        New Schema
                    </NavLink>
                </CardHeader>
                }
                <CardBody>
                    <Table columns={columns} data={list} />
                </CardBody>
            </Card>
        </PageSection>
    )

}