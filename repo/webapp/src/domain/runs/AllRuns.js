import React, { useState, useMemo, useEffect } from 'react';

import { useSelector, useDispatch } from 'react-redux'
import {
    Button,
    ButtonVariant,
    Card,
    CardHeader,
    CardBody,
    PageSection,
    Radio,
    TextInput,
    Tooltip
} from '@patternfly/react-core';
import { Spinner } from '@patternfly/react-core/dist/esm/experimental'
import { HelpIcon, SearchIcon } from '@patternfly/react-icons'
import Autosuggest from 'react-autosuggest';
import './Autosuggest.css'

import { DateTime, Duration } from 'luxon';
import { NavLink } from 'react-router-dom';

import {all, filter, suggest} from './actions';
import * as selectors from './selectors';

import Table from '../../components/Table';


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
    const suggestions = useSelector(selectors.suggestions)
    const loadingDisplay = useSelector(selectors.isFetchingSuggestions) ? "inline-block" : "none"
    useEffect(()=>{
        dispatch(all())
    },[dispatch])
    const inputProps = {
       placeholder: "Enter search query",
       value: filterQuery,
       onChange: (evt, v) => {
          let value = v.newValue
          setFilterValid(true)
          setFilterQuery(value)
          setMatchDisabled(value.trim().startsWith("$") || value.trim().startsWith("@"))
       }
    }

    const [typingTimer, setTypingTimer] = useState(null)
    const fetchSuggestions = ({value}) => {
       if (value == filterQuery) {
         return;
       }
       if (typingTimer !== null) {
          clearTimeout(typingTimer)
       }
       setTypingTimer(setTimeout(() => suggest(value)(dispatch), 1000))
    }

    return (
        <PageSection>
          <Card>
            <CardHeader>
              <div className="pf-c-input-group">
                 <Tooltip position="right" content={<span>PostgreSQL JSON path documentation</span>}>
                     <a style={{ padding: "5px 8px" }} target="_blank"
                        href="https://www.postgresql.org/docs/12/functions-json.html#FUNCTIONS-SQLJSON-PATH">
                        <HelpIcon />
                     </a>
                 </Tooltip>
                 {/* TODO: Spinner left as an excercise for the reader */}
                 <Tooltip position="bottom" content={
                    <div align="left">Enter query in one of these formats:<br />
                      - JSON keys separated by spaces or commas. Multiple keys are combined with OR (match any) or AND (match all) relation.<br />
                      - Full jsonpath query starting with <code>$</code>, e.g. <code>$.foo.bar</code>, or <code>$.foo&nbsp;?&nbsp;@.bar&nbsp;==&nbsp;0</code><br />
                      - Part of the jsonpath query starting with <code>@</code>, e.g. <code>@.bar&nbsp;==&nbsp;0</code>. This condition will be evaluated on all sub-objects.<br />
                    </div>}>
                    <Autosuggest inputProps={inputProps}
                                 suggestions={suggestions}
                                 onSuggestionsFetchRequested={fetchSuggestions}
                                 onSuggestionsClearRequested={() => {
                                    if (filterQuery === "") suggest("")(dispatch)
                                 }}
                                 getSuggestionValue={(value) => {
                                    let lastDot = filterQuery.lastIndexOf('.')
                                    return filterQuery.substring(0, lastDot + 1) + value
                                 }}
                                 renderSuggestion={v => <div>{v}</div>}
                                 renderInputComponent={ inputProps => (
                                    <input {...inputProps}
                                           {... (filterLoading ? { readonly : "" } : {}) }
                                           className="pf-c-form-control"
                                           aria-invalid={!filterValid}
                                           onKeyPress={ evt => {
                                              if (evt.key == "Enter") runFilter()
                                           }}
                                           style={{ width: "500px" }}/>
                                 )}
                                 renderSuggestionsContainer={ ({ containerProps, children, query }) => (
                                    <div {...containerProps}>
                                      <div className="react-autosuggest__loading"
                                           style={{ display: loadingDisplay }}>
                                           <Spinner size="md" />&nbsp;Loading...
                                      </div>
                                      {children}
                                    </div>
                                 )}
                                 />
                 </Tooltip>
                 <Button variant={ButtonVariant.control}
                         aria-label="search button for search input"
                         onClick={runFilter}>
                     <SearchIcon />
                 </Button>
                 {/* TODO: add some margin to the radio buttons below */}
                 <React.Fragment>
                   <Radio id="matchAny" name="matchAll" value="false" label="Match any key"
                          isChecked={!matchAll} isDisabled={matchDisabled} onChange={handleMatchAll}
                          style={{ margin: "0px 0px 0px 8px" }}/>
                   <Radio id="matchAll" name="matchAll" value="true" label="Match all keys"
                          isChecked={matchAll} isDisabled={matchDisabled} onChange={handleMatchAll}
                          style={{ margin: "0px 0px 0px 8px" }}/>
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
