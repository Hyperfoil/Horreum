import React, { useState, useMemo, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Button,
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

import { DateTime, Duration } from 'luxon';
import moment from 'moment'
import { formatDateTime, toEpochMillis } from '../../utils'

import { byTest } from './actions';
import * as selectors from './selectors';
import { tokenSelector } from '../../auth'
import { alertAction } from '../../alerts'

import { fetchTest } from '../tests/actions';
import { get } from '../tests/selectors';

import Table from '../../components/Table';
import { CellProps, UseTableOptions, UseRowSelectInstanceProps, UseRowSelectRowProps, Column, UseSortByColumnOptions } from 'react-table';
import { Run } from './reducers';

type C = CellProps<Run> & UseTableOptions<Run> & UseRowSelectInstanceProps<Run> & { row:  UseRowSelectRowProps<Run> }

//TODO how to prevent rendering before the data is loaded? (we just have start,stop,id)
const renderCell = (render: string | Function | undefined) => (arg: C) => {
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
        const useValue = (value === null || value === undefined) ? (data[index] as any)[column.id.toLowerCase()] : value;
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

type RunColumn = Column<Run> & UseSortByColumnOptions<Run>

const staticColumns: RunColumn[] = [
    {
        Header: "",
        id: "selection",
        disableSortBy: true,
        Cell: ({ row, selectedFlatRows }: C) => {
           const props = row.getToggleRowSelectedProps()
           delete props.indeterminate
           return (<input type="checkbox" {... props}
                          disabled={ !row.isSelected && selectedFlatRows.length >= 2 }/>)
        },
    }, {
        Header: "Id",
        accessor: "id",
        Cell: (arg: C) => {
            const { cell: { value } } = arg;
            return (<NavLink to={`/run/${value}`}>{value}</NavLink>)
        }
    }, {
        Header: "Executed",
        accessor: "id",
        id: "executed",
        Cell: (arg: C) => {
            const content = (<table style={{ width: "300px" }}><tbody>
                                <tr><td>Started:</td><td>{formatDateTime(arg.row.original.start)}</td></tr>
                                <tr><td>Finished:</td><td>{formatDateTime(arg.row.original.stop)}</td></tr>
                             </tbody></table>)
            return (<Tooltip isContentLeftAligned content={content}>
                      <span>{moment(toEpochMillis(arg.row.original.stop)).fromNow()}</span>
                    </Tooltip>)
        }
    }, {
        Header:"Duration",
        id: "duration",
        accessor: (run: Run) => Duration.fromMillis(toEpochMillis(run.stop) - toEpochMillis(run.start)).toFormat("hh:mm:ss.SSS")
    }, {
        Header: "Schema",
        accessor: "schema",
        Cell: (arg: C) => {
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
    const [selectedRows, setSelectedRows] = useState<Record<string, boolean>>({})
    const tableColumns = useMemo(() => {
        const rtrn = [ ...staticColumns ]
        columns.forEach((col, index) => {
             rtrn.push({
                 Header: col.headerName,
                 accessor: (run: Run) => run.view && run.view[index],
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
        document.title = (test ? test.name : "Loading...") + " | Horreum"
        if (test && test.defaultView) {
            setColumns(test.defaultView.components)
        }
    }, [test])
    const isLoading = useSelector(selectors.isLoading)

    const [actualCompareUrl, compareError] = useMemo(() => {
       if ( test && test.compareUrl && typeof test.compareUrl === "function" ) {
          try {
             const rows = Object.keys(selectedRows).map(id => runs ? runs[parseInt(id)].id : [])
             if (rows.length >= 2) {
                return [test.compareUrl(rows), undefined]
             }
          } catch (e) {
             return [undefined, e]
          }
       }
       return [undefined, undefined]
    }, [test ? test.compareUrl : undefined, runs, selectedRows])
    const hasError = !!compareError
    useEffect(() => {
       if (compareError) {
          dispatch(alertAction("COMPARE_FAILURE", "Compare function failed", compareError))
       }
    }, [hasError])

    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                        <ToolbarGroup>
                            <ToolbarItem className="pf-u-mr-xl">{`Test: ${test && test.name || testId}`}</ToolbarItem>
                        </ToolbarGroup>
                        { test && test.compareUrl &&
                        <ToolbarGroup>
                           <ToolbarItem>
                              <Button variant="primary"
                                      component="a"
                                      target="_blank"
                                      href={ actualCompareUrl || "" }
                                      isDisabled={ !actualCompareUrl }>Compare runs</Button>
                           </ToolbarItem>
                        </ToolbarGroup>
                        }
                        <ToolbarGroup>
                            <NavLink to={ `/test/${testId}` } ><EditIcon /></NavLink>
                        </ToolbarGroup>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Table columns={tableColumns}
                           data={runs || []}
                           initialSortBy={[{id: "stop", desc: true}]}
                           isLoading={isLoading}
                           selected={selectedRows}
                           onSelected={setSelectedRows}
                           />
                </CardBody>
            </Card>
        </PageSection>
    )
}
