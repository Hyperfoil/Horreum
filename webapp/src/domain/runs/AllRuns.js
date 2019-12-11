import React, { useMemo, useEffect } from 'react';

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

import {all} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';
  

export default ()=>{
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
    const allRuns = useSelector(selectors.all);
    useEffect(()=>{
        dispatch(all())
    },[dispatch])
    return (
        <PageSection>
          <Card>
            <CardHeader>

            </CardHeader>
            <CardBody>
              <Table columns={columns} data={allRuns} />
            </CardBody>
          </Card>
        </PageSection>
    )
}
