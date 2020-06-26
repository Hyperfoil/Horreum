import React, { useState, useMemo, useEffect, KeyboardEvent } from 'react';

import { useSelector, useDispatch } from 'react-redux'
import {
    Button,
    ButtonVariant,
    Card,
    CardHeader,
    CardBody,
    Checkbox,
    PageSection,
    Radio,
    Spinner,
    Tooltip,
} from '@patternfly/react-core';
import {
    FolderOpenIcon,
    HelpIcon,
    SearchIcon,
    TrashIcon,
} from '@patternfly/react-icons'
import Autosuggest, { SuggestionsFetchRequestedParams, InputProps, ChangeEvent } from 'react-autosuggest';
import './Autosuggest.css'

import { Duration } from 'luxon';
import { NavLink } from 'react-router-dom';
import { CellProps, Column } from 'react-table'

import {all, filter, suggest, selectRoles } from './actions';
import * as selectors from './selectors';
import { isAuthenticatedSelector, registerAfterLogin, roleToName } from '../../auth'
import { toEpochMillis } from '../../utils'

import Table from '../../components/Table';
import AccessIcon from '../../components/AccessIcon';
import OwnerSelect from '../../components/OwnerSelect';
import { Run } from './reducers';
import { ExecutionTime, Menu } from './components'

type C = CellProps<Run>

export default ()=>{
    document.title = "Runs | Horreum"
    const [showTrashed, setShowTrashed] = useState(false)
    const runs = useSelector(selectors.filter(showTrashed))
    const isAuthenticated = useSelector(isAuthenticatedSelector)

    const [filterQuery, setFilterQuery] = useState("")
    const [filterValid, setFilterValid] = useState(true)
    const [filterLoading, setFilterLoading] = useState(false)
    const [matchDisabled, setMatchDisabled] = useState(false)
    const [matchAll, setMatchAll] = useState(false)

    const dispatch = useDispatch();
    const columns: Column<Run>[] = useMemo(()=>[
        {
          Header:"Id",
          accessor:"id",
          Cell: (arg: C) => {
            const {cell: {value} } = arg;
            return (<><NavLink to={`/run/${value}`}>{value}</NavLink>
               { arg.row.original.trashed &&
                  <TrashIcon style={{ fill: "#888", marginLeft: "10px" }} /> }
            </>)
            }
        }, {
          Header: "Access",
          accessor: "access",
          Cell: (arg: C) => <AccessIcon access={arg.cell.value} />
        }, {
          Header: "Owner",
          accessor:"owner",
          Cell: (arg: C) => roleToName(arg.cell.value)
        },
        {
          Header: "Executed",
          id: "executed",
          Cell: (arg: C) => ExecutionTime(arg.row.original)
        }, {
          Header:"Duration",
          id: "duration",
          accessor: (v: Run) => Duration.fromMillis(toEpochMillis(v.stop) - toEpochMillis(v.start)).toFormat("hh:mm:ss.SSS")
        }, {
          Header: "Test",
          accessor: "testid",
          Cell: (arg: C) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/run/list/${value}`}>{arg.row.original.testname} <FolderOpenIcon /></NavLink>)
          }
        }, {
          Header:"Actions",
          id: "actions",
          accessor: "id",
          Cell: (arg: CellProps<Run, number>) => Menu(arg.row.original)
        }
    ],[dispatch])

    const selectedRoles = useSelector(selectors.selectedRoles)

    const runFilter = (roles: string) => {
       setFilterLoading(true)
       dispatch(filter(filterQuery, matchAll, roles, success => {
         setFilterLoading(false);
         setFilterValid(success);
       }))
    };
    const handleMatchAll = (checked: boolean, evt: React.ChangeEvent<any>) => {
       if (checked) setMatchAll(evt.target.value === "true")
    }
    const suggestions = useSelector(selectors.suggestions)
    const loadingDisplay = useSelector(selectors.isFetchingSuggestions) ? "inline-block" : "none"
    useEffect(()=>{
        dispatch(all(showTrashed))
        dispatch(registerAfterLogin("reload_runs", () => {
           dispatch(all())
           runFilter(selectedRoles.key)
        }))
    },[dispatch, showTrashed])

    const inputProps: InputProps<string> = {
       placeholder: "Enter search query",
       value: filterQuery,
       onChange: (evt: React.FormEvent<any>, v: ChangeEvent) => {
          // TODO
          let value = (v as any).newValue
          setFilterValid(true)
          setFilterQuery(value)
          setMatchDisabled(value.trim().startsWith("$") || value.trim().startsWith("@"))
       },
       onKeyDown: (evt: KeyboardEvent<Element>) => {
          if (evt.key === " " && evt.ctrlKey) {
             fetchSuggestionsNow()
          }
       }
    }
    const [typingTimer, setTypingTimer] = useState<number | null>(null)
    const fetchSuggestions = ({value}: SuggestionsFetchRequestedParams) => {
       if (value === filterQuery) {
         return;
       }
       if (typingTimer !== null) {
          clearTimeout(typingTimer)
       }
       setTypingTimer(window.setTimeout(() => suggest(value, selectedRoles.key)(dispatch), 1000))
    }
    const fetchSuggestionsNow = () => {
       if (typingTimer !== null) {
          clearTimeout(typingTimer)
       }
       suggest(filterQuery, selectedRoles.key)(dispatch)
    }
    const isLoading = useSelector(selectors.isLoading)
    return (
        <PageSection>
          <Card>
            <CardHeader>
              <div className="pf-c-input-group">
                 <Tooltip position="right" content={<span>PostgreSQL JSON path documentation</span>}>
                     <a style={{ padding: "5px 8px" }} target="_blank" rel="noopener noreferrer"
                        href="https://www.postgresql.org/docs/12/functions-json.html#FUNCTIONS-SQLJSON-PATH">
                        <HelpIcon />
                     </a>
                 </Tooltip>
                 {/* TODO: Spinner left as an excercise for the reader */}
                 <Tooltip position="bottom" content={
                    <div style={{ textAlign: "left" }}>Enter query in one of these formats:<br />
                      - JSON keys separated by spaces or commas. Multiple keys are combined with OR (match any) or AND (match all) relation.<br />
                      - Full jsonpath query starting with <code>$</code>, e.g. <code>$.foo.bar</code>, or <code>$.foo&nbsp;?&nbsp;@.bar&nbsp;==&nbsp;0</code><br />
                      - Part of the jsonpath query starting with <code>@</code>, e.g. <code>@.bar&nbsp;==&nbsp;0</code>. This condition will be evaluated on all sub-objects.<br />
                    </div>
                 }><>
                    { /* TODO: It seems Patternfly has this as Select variant={SelectVariant.typeahead} */ }
                    <Autosuggest inputProps={inputProps}
                                 suggestions={suggestions}
                                 onSuggestionsFetchRequested={fetchSuggestions}
                                 onSuggestionsClearRequested={() => {
                                    if (filterQuery === "") suggest("", selectedRoles.key)(dispatch)
                                 }}
                                 getSuggestionValue={(value) => {
                                    let quoted = false;
                                    for (let i = filterQuery.length; i >= 0; --i) {
                                       switch (filterQuery.charAt(i)) {
                                          // we're not handling escaped quotes...
                                          case '"':
                                             quoted = !quoted;
                                             break;
                                          case '.':
                                          case ']':
                                             if (!quoted) {
                                                return filterQuery.substring(0, i + 1) + value
                                             }
                                             break;
                                          default:
                                       }
                                    }
                                    return value;
                                 }}
                                 renderSuggestion={v => <div>{v}</div>}
                                 renderInputComponent={ (inputProps: InputProps<string>) => (
                                    <input {...inputProps as any}
                                           {... (filterLoading ? { readOnly : true } : {}) }
                                           className="pf-c-form-control"
                                           aria-invalid={!filterValid}
                                           onKeyPress={ evt => {
                                              if (evt.key === "Enter") runFilter(selectedRoles.key)
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
                 </></Tooltip>
                 <Button variant={ButtonVariant.control}
                         aria-label="search button for search input"
                         onClick={() => runFilter(selectedRoles.key)}>
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
                 { isAuthenticated &&
                 <div style={{ width: "200px", marginLeft: "16px" }}>
                    <OwnerSelect includeGeneral={true}
                                 selection={selectedRoles.toString()}
                                 onSelect={selection => {
                                    dispatch(selectRoles(selection))
                                    runFilter(selection.key)
                                 }} />
                 </div>
                 }
                 <span style={{ width: "20px" }} />
                 <Checkbox id="showTrashed" aria-label="show trashed runs"
                           label="Show trashed runs"
                           isChecked={ showTrashed }
                           onChange={ setShowTrashed } />
              </div>
            </CardHeader>
            <CardBody>
              <Table columns={columns} data={runs || []} initialSortBy={[{id: "stop", desc: true}]} isLoading={ isLoading }/>
            </CardBody>
          </Card>
        </PageSection>
    )
}
