import React, { useState, useMemo, useEffect } from 'react';

import { useSelector } from 'react-redux'
import { useDispatch } from 'react-redux'
import {
    Card,
    CardHeader,
    CardBody,
    PageSection,
} from '@patternfly/react-core';
import { DateTime, Duration } from 'luxon';
import { NavLink } from 'react-router-dom';

import {all, filter} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';
import { Button, ButtonVariant, TextInput, Tooltip, TooltipPosition } from '@patternfly/react-core';
import { SearchIcon } from '@patternfly/react-icons'
  

export default ()=>{
    const [filterQuery, setFilterQuery] = useState("")
    const [filterLoading, setFilterLoading] = useState(false)
    const columns = useMemo(()=>[
        {
          Header:"Id",accessor:"id",
          Cell: (arg) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/run/${value}`}>{value}</NavLink>)
            }
        },
        {Header:"Start",accessor:v=>DateTime.fromMillis(v.start).toFormat("yyyy-LL-dd HH:mm:ss ZZZ")},
        {Header:"Stop",accessor:v=>DateTime.fromMillis(v.stop).toFormat("yyyy-LL-dd HH:mm:ss ZZZ")},
        {Header:"Duration",accessor:v => Duration.fromMillis(v.stop - v.start).toFormat("hh:mm:ss.SSS")},
        {
          Header:"Test",accessor:"testid",
          Cell: (arg) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/run/list/${value}`}>{value}</NavLink>)
          }
        },
    ],[])
    const dispatch = useDispatch();
    const runs = useSelector(selectors.filter)
    const runFilter = () => {
       setFilterLoading(true)
       dispatch(filter(filterQuery, () => setFilterLoading(false)));
    };
    useEffect(()=>{
        dispatch(all())
    },[dispatch])
    return (
        <PageSection>
          <Card>
            <CardHeader>
              <div className="pf-c-input-group">
                 {/* TODO: Spinner left as an excercise for the reader */}
                 <Tooltip position="bottom" content={<div>Enter JSON keys separated by spaces or commas. Multiple keys are combined with OR relation.</div>}>
                    <TextInput value={filterQuery} onChange={setFilterQuery}
                               isReadOnly={filterLoading} type="search"
                               aria-label="search expression"
                               placeholder="Enter search expression..." />
                    <Button variant={ButtonVariant.control}
                            aria-label="search button for search input"
                            onClick={runFilter}>
                      <SearchIcon />
                    </Button>
                 </Tooltip>
              </div>
            </CardHeader>
            <CardBody>
              <Table columns={columns} data={runs} />
            </CardBody>
          </Card>
        </PageSection>
    )
}
