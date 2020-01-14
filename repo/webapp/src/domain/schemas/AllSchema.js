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
import Table from '../../components/Table';

export default () => {
    const columns = useMemo(() => [
        {
            Header: "Name", accessor: "name"
        },
        {
            Header: "Description", accessor: "description"
        },
        {
            Header: "json-schema", accessor: "schema",
            Cell: (arg) => {
                const { cell: { value, row: { index } }, data } = arg;
                return (<a href={`/api/schema/${data[index].name}`} target="_blank">link</a>)
            }
        },
        {
            Header: "", accessor: "id", disableSortBy: true,
            Cell: (arg) => {
                const { cell: { value } } = arg;
                return (<>
                    <Button
                        variant="link"
                        style={{ color: "#a30000" }}

                    >
                    </Button>
                </>)
            }
        }
    ], [])
    const dispatch = useDispatch();
    const list = useSelector(selectors.all);
    useEffect(() => {
        dispatch(actions.all())
    }, [dispatch])
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <NavLink className="pf-c-button pf-m-primary" to="/schema/_new">
                        New Schema
                    </NavLink>
                </CardHeader>
                <CardBody>
                    <Table columns={columns} data={list} />
                </CardBody>
            </Card>
        </PageSection>
    )

}