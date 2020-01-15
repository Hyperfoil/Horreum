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
import { Button, ButtonVariant, Radio, Switch, TextInput, Tooltip, TooltipPosition } from '@patternfly/react-core';
import { SearchIcon } from '@patternfly/react-icons'
  

export default ()=>{
    const [filterQuery, setFilterQuery] = useState("")
    const [filterValid, setFilterValid] = useState(true)
    const [filterLoading, setFilterLoading] = useState(false)
    const [matchDisabled, setMatchDisabled] = useState(false)
    const [matchAll, setMatchAll] = useState(false)
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
       dispatch(filter(filterQuery, matchAll, success => {
         setFilterLoading(false);
         setFilterValid(success);
       }))
    };
    const handleMatchAll = (v, evt) => {
       if (v) setMatchAll(evt.target.value == "true")
    }
    useEffect(()=>{
        dispatch(all())
    },[dispatch])
    return (
        <PageSection>
          <Card>
            <CardHeader>
              <div className="pf-c-input-group">
                 {/* TODO: Spinner left as an excercise for the reader */}
                 <Tooltip position="bottom" content={
                    <div align="left">Enter query in one of these formats:<br />
                      - JSON keys separated by spaces or commas. Multiple keys are combined with OR (match any) or AND (match all) relation.<br />
                      - Full jsonpath query starting with <code>$</code>, e.g. <code>$.foo.bar</code>, or <code>$.foo&nbsp;?&nbsp;@.bar&nbsp;==&nbsp;0</code><br />
                      - Part of the jsonpath query starting with <code>@</code>, e.g. <code>@.bar&nbsp;==&nbsp;0</code>. This condition will be evaluated on all sub-objects.<br />
                    </div>}>
                    <TextInput value={filterQuery} onChange={value => {
                                 setFilterValid(true)
                                 setFilterQuery(value)
                                 setMatchDisabled(value.trim().startsWith("$") || value.trim().startsWith("@"))
                               }}
                               onKeyPress={ evt => {
                                  if (evt.key == "Enter") runFilter()
                               }}
                               isReadOnly={filterLoading}
                               isValid={filterValid}
                               type="search"
                               aria-label="search expression"
                               placeholder="Enter search expression..." />
                 </Tooltip>
                 <Button variant={ButtonVariant.control}
                         aria-label="search button for search input"
                         onClick={runFilter}>
                     <SearchIcon />
                 </Button>
                 {/* TODO: add some margin to the radio buttons below */}
                 <React.Fragment>
                   <Radio id="matchAny" name="matchAll" value="false" label="Match any key"
                          isChecked={!matchAll} isDisabled={matchDisabled} onChange={handleMatchAll}/>
                   <Radio id="matchAll" name="matchAll" value="true" label="Match all keys"
                          isChecked={matchAll} isDisabled={matchDisabled} onChange={handleMatchAll}/>
                 </React.Fragment>
              </div>
            </CardHeader>
            <CardBody>
              <Table columns={columns} data={runs} />
            </CardBody>
          </Card>
        </PageSection>
    )
}
