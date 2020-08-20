import React, { useMemo, useEffect, Dispatch } from 'react';
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Card,
    CardHeader,
    CardBody,
    PageSection,
} from '@patternfly/react-core';
import { NavLink } from 'react-router-dom';

import * as actions from './actions';
import * as selectors from './selectors';
import { useTester, registerAfterLogin, roleToName } from '../../auth'
import { alertAction } from '../../alerts'
import Table from '../../components/Table';
import AccessIcon from '../../components/AccessIcon';
import ActionMenu from '../../components/ActionMenu';
import { CellProps, Column } from 'react-table';
import { Schema, SchemaDispatch } from './reducers';

type C = CellProps<Schema>

export default () => {
    document.title = "Schemas | Horreum"
    const thunkDispatch = useDispatch<SchemaDispatch>()
    const dispatch = useDispatch()
    const columns: Column<Schema>[] = useMemo(() => [
        {
            Header: "Access", accessor:"access",
            Cell: (arg: C) => <AccessIcon access={arg.cell.value} />
        },
        {
            Header: "Owner", accessor:"owner",
            Cell: (arg: C) => roleToName(arg.cell.value)},
        {
            Header: "Name", accessor: "name",
            Cell: (arg: C) => { return (
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
                        token={ arg.row.original.token || undefined }
                        tokenToLink={ (id, token) => "/schema/" + id + "?token=" + token }
                        onTokenReset={ id => dispatch(actions.resetToken(id)) }
                        onTokenDrop={ id => dispatch(actions.dropToken(id)) }
                        onAccessUpdate={ (id, owner, access) => thunkDispatch(actions.updateAccess(id, owner, access)).catch(e => {
                            dispatch(alertAction("SCHEMA_UPDATE", "Schema update failed", e))
                        }) }
                        description={ "schema " + arg.row.original.name + " (" + arg.row.original.uri + ")" }
                        onDelete={ id => thunkDispatch(actions.deleteSchema(id)).catch((e: any) => {
                            dispatch(alertAction("SCHEMA_DELETE", "Failed to delete schema", e))
                        }) }/>
                )
            }
        }
    ], [dispatch])
    const list = useSelector(selectors.all);
    useEffect(() => {
        dispatch(actions.all())
        dispatch(registerAfterLogin("reload_schemas", () => {
           dispatch(actions.all())
        }))
    }, [dispatch])
    const isTester = useTester()
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
                    <Table columns={columns} data={list || []} />
                </CardBody>
            </Card>
        </PageSection>
    )

}