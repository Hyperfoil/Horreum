import React, { useState, useMemo, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Card,
    CardHeader,
    CardBody,
    PageSection,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    Tooltip,
} from '@patternfly/react-core';
import {
    EditIcon,
    WarningTriangleIcon,
} from '@patternfly/react-icons';
import { NavLink } from 'react-router-dom';

import { byTest } from './actions';
import * as selectors from './selectors';
import { tokenSelector } from '../../auth'

import { fetchTest } from '../tests/actions';
import { get } from '../tests/selectors';

import Table from '../../components/Table';

//TODO how to prevent rendering before the data is loaded? (we just have start,stop,id)
const renderCell = (render) => (arg) => {
    const { cell: { value, row: { index } }, data, column } = arg;
    if (!render) {
        if (typeof value === "object") {
            return JSON.stringify(value)
        }
        return value
    } else if (typeof render === "string") {
        return (<Tooltip content={ "Render failure: " + render } ><WarningTriangleIcon style={{color: "#a30000"}} /></Tooltip>);
    }
    const token = useSelector(tokenSelector)
    try {
        const useValue = (value === null || value === undefined) ? data[index][column.id.toLowerCase()] : value;
        const rendered = render(useValue, data[index], token)
        if (!rendered) {
            return "-"
        } else if (typeof rendered === "string") {
            //this is a hacky way to see if it looks like html :)
            if (rendered.trim().startsWith("<") && rendered.trim().endsWith(">")) {
                //render it as html
                return (<div dangerouslySetInnerHTML={{ __html: rendered }} />)
            } else {
                return rendered;
            }
        } else {
            return rendered;
        }
    } catch (e) {
        return "--"
    }
}


const staticColumns = [
    {
        Header: "Id",
        accessor: "id",
        Cell: (arg) => {
            const { cell: { value } } = arg;
            return (<NavLink to={`/run/${value}`}>{value}</NavLink>)
        }
    }, {
        Header: "Start",
        id: "start",
        accessor: v => window.DateTime.fromMillis(v.start).toFormat("yyyy-LL-dd HH:mm:ss ZZZ")
    }, {
        Header: "Stop",
        id: "stop",
        accessor: v => window.DateTime.fromMillis(v.stop).toFormat("yyyy-LL-dd HH:mm:ss ZZZ")
    }, {
        Header: "Schema",
        accessor: "schema",
        Cell: (arg) => {
            const { cell: { value } } = arg;
            // LEFT JOIN results in schema.id == 0
            if (value) {
               return Object.keys(value).map(key => (<><NavLink key={key} to={`/schema/${key}`}>{value[key]}</NavLink>&nbsp;</>))
            } else {
               return "--"
            }
        }
    }
]

export default () => {
    const { testId } = useParams();
    const test = useSelector(get(testId))
    const [columns, setColumns] = useState((test && test.defaultView) ? test.defaultView.components : [])
    const tableColumns = useMemo(() => {
        const rtrn = [...staticColumns]
        columns.forEach((col, index) => {
             rtrn.push({
                 Header: col.headerName,
                 accessor: `view[${index}]`,
                 Cell: renderCell(col.render)
             })
        })
        return rtrn;
    }, [columns]);

    const dispatch = useDispatch();
    const runs = useSelector(selectors.testRuns(testId));
    useEffect(() => {
        dispatch(fetchTest(testId));
    }, [dispatch, testId])
    useEffect(() => {
        dispatch(byTest(testId))
    }, [dispatch])
    useEffect(() => {
        document.title = test.name + " | Horreum"
        if (test && test.defaultView) {
            setColumns(test.defaultView.components)
        }
    }, [test])
    const isLoading = useSelector(selectors.isLoading)
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                        <ToolbarGroup>
                            <ToolbarItem className="pf-u-mr-xl">{`Test: ${test.name || testId}`}</ToolbarItem>
                        </ToolbarGroup>
                        <ToolbarGroup>
                            <NavLink to={ `/test/${testId}` } ><EditIcon /></NavLink>
                        </ToolbarGroup>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Table columns={tableColumns} data={runs} initialSortBy={[{id: "stop", desc: true}]} isLoading={isLoading}/>
                </CardBody>
            </Card>
        </PageSection>
    )
}
