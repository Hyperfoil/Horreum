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

import {fetchSummary} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';
  

export default ()=>{
    const columns = useMemo(()=>[
        {
          Header:"Id",accessor:"id",
          Cell: (arg) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/test/${value}`}>{value}</NavLink>)
          }
        },
        {Header:"Name",accessor:"name"},
        {Header:"Description",accessor:"description"},
        {
          Header:"Run Count",accessor:"count",
          Cell: (arg) => {
            const {cell: {value, row: {index}}, data} = arg;
            return (<NavLink to={`/run/list/${data[index].id}`}>{value}</NavLink>)
          }
      },
        {
          Header:"Schema",accessor: 'haschema',
          Cell: (arg) => { 
          const {cell: {value} } = arg
          return (value === true ? <i className="fas fa-check" /> : null)
        }      
      },
    ],[])
    const dispatch = useDispatch();
    const allRuns = useSelector(selectors.all);
    useEffect(()=>{
        dispatch(fetchSummary())
    },[])
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
