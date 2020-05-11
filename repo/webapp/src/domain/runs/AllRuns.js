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
    Tooltip
} from '@patternfly/react-core';
import { Spinner } from '@patternfly/react-core/dist/esm/experimental'
import {
    FolderOpenIcon,
    HelpIcon,
    SearchIcon,
} from '@patternfly/react-icons'
import Autosuggest from 'react-autosuggest';
import './Autosuggest.css'

import { DateTime, Duration } from 'luxon';
import * as moment from 'moment'
import { NavLink } from 'react-router-dom';

import {all, filter, suggest, selectRoles, resetToken, dropToken, updateAccess } from './actions';
import * as selectors from './selectors';
import { isAuthenticatedSelector, rolesSelector, registerAfterLogin, roleToName } from '../../auth.js'

import Table from '../../components/Table';
import AccessIcon from '../../components/AccessIcon';
import OwnerSelect from '../../components/OwnerSelect';
import ActionMenu from '../../components/ActionMenu';

export default ()=>{
    document.title = "Runs | Horreum"
    const runs = useSelector(selectors.filter)
    const roles = useSelector(rolesSelector)
    const isAuthenticated = useSelector(isAuthenticatedSelector)

    const [filterQuery, setFilterQuery] = useState("")
    const [filterValid, setFilterValid] = useState(true)
    const [filterLoading, setFilterLoading] = useState(false)
    const [matchDisabled, setMatchDisabled] = useState(false)
    const [matchAll, setMatchAll] = useState(false)

    const dispatch = useDispatch();
    const columns = useMemo(()=>[
        {
          Header:"Id",
          accessor:"id",
          Cell: (arg) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/run/${value}`}>{value}</NavLink>)
            }
        }, {
          Header: "Access",
          accessor: "access",
          Cell: (arg) => <AccessIcon access={arg.cell.value} />
        }, {
          Header: "Owner",
          accessor:"owner",
          Cell: (arg) => roleToName(arg.cell.value)
        },
        {
          Header: "Executed",
          id: "executed",
          Cell: arg => {
            const format = time => DateTime.fromMillis(time).toFormat("yyyy-LL-dd HH:mm:ss ZZZ")
            const content = (<table style={{ width: "300px" }}>
                              <tr><td>Started:</td><td>{format(arg.row.original.start)}</td></tr>
                              <tr><td>Finished:</td><td>{format(arg.row.original.stop)}</td></tr>
                             </table>)
            return (<Tooltip isContentLeftAligned content={content}>
                        <span>{moment(arg.row.original.stop).fromNow()}</span>
                    </Tooltip>)
          }
        }, {
          Header:"Duration",
          id: "duration",
          accessor: v => Duration.fromMillis(v.stop - v.start).toFormat("hh:mm:ss.SSS")
        }, {
          Header:"Test",
          accessor:"testid",
          Cell: (arg) => {
            const {cell: {value} } = arg;
            return (<NavLink to={`/run/list/${value}`}>{arg.row.original.testname} <FolderOpenIcon /></NavLink>)
          }
        }, {
          Header:"Actions",
          id: "actions",
          accessor: "id",
          Cell: (arg) => {
            return (
             <ActionMenu id={arg.cell.value}
                         owner={ arg.row.original.owner }
                         access={ arg.row.original.access }
                         token={ arg.row.original.token }
                         tokenToLink={ (id, token) => "/run/" + id + "?token=" + token }
                         onTokenReset={ id => dispatch(resetToken(id)) }
                         onTokenDrop={ id => dispatch(dropToken(id)) }
                         onAccessUpdate={ (id, owner, access) => dispatch(updateAccess(id, owner, access)) } />
          )}
        }
    ],[roles, dispatch])

    const selectedRoles = useSelector(selectors.selectedRoles)

    const runFilter = (roles) => {
       setFilterLoading(true)
       dispatch(filter(filterQuery, matchAll, roles, success => {
         setFilterLoading(false);
         setFilterValid(success);
       }))
    };
    const handleMatchAll = (v, evt) => {
       if (v) setMatchAll(evt.target.value === "true")
    }
    const suggestions = useSelector(selectors.suggestions)
    const loadingDisplay = useSelector(selectors.isFetchingSuggestions) ? "inline-block" : "none"
    useEffect(()=>{
        dispatch(all())
        dispatch(registerAfterLogin("reload_runs", () => {
           dispatch(all())
           runFilter(selectedRoles.key)
        }))
    },[dispatch])

    const inputProps = {
       placeholder: "Enter search query",
       value: filterQuery,
       onChange: (evt, v) => {
          let value = v.newValue
          setFilterValid(true)
          setFilterQuery(value)
          setMatchDisabled(value.trim().startsWith("$") || value.trim().startsWith("@"))
       },
       onKeyDown: (evt) => {
          if (evt.key === " " && evt.ctrlKey) {
             fetchSuggestionsNow()
          }
       }
    }
    const [typingTimer, setTypingTimer] = useState(null)
    const fetchSuggestions = ({value}) => {
       if (value === filterQuery) {
         return;
       }
       if (typingTimer !== null) {
          clearTimeout(typingTimer)
       }
       setTypingTimer(setTimeout(() => suggest(value, selectedRoles.key)(dispatch), 1000))
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
                    <div align="left">Enter query in one of these formats:<br />
                      - JSON keys separated by spaces or commas. Multiple keys are combined with OR (match any) or AND (match all) relation.<br />
                      - Full jsonpath query starting with <code>$</code>, e.g. <code>$.foo.bar</code>, or <code>$.foo&nbsp;?&nbsp;@.bar&nbsp;==&nbsp;0</code><br />
                      - Part of the jsonpath query starting with <code>@</code>, e.g. <code>@.bar&nbsp;==&nbsp;0</code>. This condition will be evaluated on all sub-objects.<br />
                    </div>}>
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
                                 renderInputComponent={ inputProps => (
                                    <input {...inputProps}
                                           {... (filterLoading ? { readOnly : "" } : {}) }
                                           className="pf-c-form-control"
                                           aria-invalid={!filterValid}
                                           onKeyPress={ evt => {
                                              if (evt.key === "Enter") runFilter()
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
              </div>
            </CardHeader>
            <CardBody>
              <Table columns={columns} data={runs} initialSortBy={[{id: "stop", desc: true}]} isLoading={ isLoading }/>
            </CardBody>
          </Card>
        </PageSection>
    )
}

