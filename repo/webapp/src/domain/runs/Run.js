import React, { useState, useRef, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector, useDispatch } from 'react-redux'
import { DateTime } from 'luxon';
import { Spinner } from '@patternfly/react-core';
import jsonpath from 'jsonpath';

import * as actions from './actions';
import * as selectors from './selectors';
import { isTesterSelector } from '../../auth'

import Editor from '../../components/Editor/monaco/Editor';
import SchemaSelect from '../../components/SchemaSelect';

//import Editor from '../../components/Editor';
import {
    Button,
    ButtonVariant,
    Card,
    CardHeader,
    CardBody,
    Dropdown,
    DropdownToggle,
    DropdownItem,
    InputGroup,
    Popover,
    Toolbar,
    ToolbarSection,
} from '@patternfly/react-core';
import { HelpIcon } from '@patternfly/react-icons'
import { toString } from '../../components/Editor';
import { NavLink } from 'react-router-dom';
import Autosuggest from 'react-autosuggest'

export default () => {
    const { id } = useParams();
    const editor = useRef();
    const run = useSelector(selectors.get(id));
    const [data, setData] = useState(toString(run.data) || "{}")
    const [schemaUri, setSchemaUri] = useState(run.schemaUri)
    const isTester = useSelector(isTesterSelector)

    const [pathQuery, setPathQuery] = useState("")
    const [pathInvalid, setPathInvalid] = useState(false)
    const [pathType, setPathType] = useState('js')
    const [pathTypeOpen, setPathTypeOpen] = useState(false)
    const [pathSuggestions, setPathSuggestions] = useState([])
    const dispatch = useDispatch();
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get('token')
        dispatch(actions.get(id, token))
    }, [dispatch, id])
    useEffect(() => {
        //change the loaded document when the run changes
        setData(toString(run.data) || "{}");
        setSchemaUri(run.schemaUri);
    }, [run])

    const inputProps = {
        placeholder: "Enter selection path, e.g. $.foo[?(@.bar > 42)]",
        value: pathQuery,
        onChange: (evt, v) => {
            setPathInvalid(false)
            setPathQuery(v.newValue)
        },
        onKeyDown: evt => {
            if (evt.key === " " && evt.ctrlKey) {
                updateSuggestions(pathQuery);
            } else if (evt.key === "Enter") {
                runPathQuery();
            }
        }
    }
    const postgresTojsJsonPath = query => {
       query = query.replace(/\.\*\*\.?/g, "..");
       query = query.replace(/\."([^"]*)"/g, "['$1']")
       query = query.replace(/ *\? */, '?')

       // Note: postgres query allows conditions beyond selecting array items
       let qIndex = query.indexOf('?');
       if (qIndex >= 0) {
          query = query.substring(0, qIndex) + "[" + query.substring(qIndex) + "]"
       }
       return query;
    }
    const runPathQuery = evt => {
        if (pathQuery === "") {
            setPathInvalid(false);
            setData(toString(run.data) || "{}");
        } else {
            let query = postgresTojsJsonPath(pathQuery);
            while (query.endsWith(".")) {
               query = query.substring(0, query.length - 1);
            }
            if (query.startsWith("@")) {
               query = "$..*[?(" + query + ")]";
            } else if (!query.startsWith("$")) {
               query = "$.." + query
            }
            try {
                const found = jsonpath.nodes(run.data, query).map(({path, value}) => {
                  let obj = {}
                  var combinedPath = "";
                  path.forEach(x => {
                     if (combinedPath === "") {
                        combinedPath = x;
                     } else {
                        if (typeof(x) === "number") {
                           combinedPath = combinedPath + "[" + x + "]"
                        } else if (x.match(/^[a-zA-Z0-9_]*$/)) {
                           combinedPath = combinedPath + "." + x
                        } else {
                           combinedPath = combinedPath + '."' + x.replace(/"/g, '\\"') + '"';
                        }
                     }
                  })
                  obj[combinedPath] = value
                  return obj
                })
                setPathInvalid(false)
                setData(JSON.stringify(found, null, 2))
            } catch (e) {
                console.log("Failed query: " + query)
                setPathInvalid(true)
                setData(e.message)
            }
        }
    }
    const typingTimer = useRef(null)
    const delayedUpdateSuggestions = ({value}) => {
       if (typingTimer.current !== null) {
          clearTimeout(typingTimer.current)
       }
       typingTimer.current = setTimeout(() => updateSuggestions(value), 1000)
    }
    const updateSuggestions = (value) => {
       let query = postgresTojsJsonPath(value.trim())
       let conditionStart = query.indexOf("@")
       if (conditionStart >= 0) {
          var condition = query.substring(conditionStart + 1);
          let conditionEnd = Math.min(...[ "<", ">", "!=", "==", " ", ")"].map(op => {
             let opIndex = condition.indexOf(op)
             return opIndex >= 0 ? opIndex : condition.length;
          }))
          condition = condition.substring(0, conditionEnd);
          let qIndex = query.indexOf('?')
          if (qIndex > 0) {
             // condition start looks like [?(@...
             query = query.substring(0, qIndex - 1).trim() + condition;
          } else {
             query = query.substring(0, conditionStart) + condition;
          }
       }
       let lastDot = Math.max(query.lastIndexOf('.'), query.lastIndexOf(']'));
       let incomplete = ""
       if (lastDot >= 0) {
          incomplete = query.substring(lastDot + 1)
          if (incomplete === "*") {
             incomplete = ""
          }
          query = query.substring(0, lastDot + 1);
          if (query.endsWith(".")) {
             query = query + "*"
          }
          if (!query.startsWith("$")) {
             query = "$.." + query
          } else if (query === "$") {
             query = "$.*"
          }
       } else if (query === "$") {
          // do not offer anything at this point
          setPathSuggestions([])
          return;
       } else {
          incomplete = query;
          query = "$..*"
       }
       try {
          let sgs = jsonpath.paths(run.data, query)
               .map(path => path[path.length - 1].toString())
               .filter(k => k.startsWith(incomplete))
               .map(k => k.match(/^[a-zA-Z0-9_]*$/) ? k : '"' + k + '"')
          setPathSuggestions([ ...new Set(sgs)].sort())
       } catch (e) {
          console.log("Failed query: " + query)
          setPathSuggestions([])
          setPathInvalid(true)
       }
    }
    const updateSuggestionValue = (value) => {
       let quoted = false;
       let lastDot = 0;
       let lastClosingSquareBracket = 0;
       outer: for (let i = pathQuery.length; i >= 0; --i) {
          switch (pathQuery.charAt(i)) {
             // we're not handling escaped quotes...
             case '"':
                quoted = !quoted;
                break;
             case '.':
                if (!quoted) {
                   lastDot = i;
                   break outer;
                }
                break;
             case ']':
                if (!quoted) {
                   lastClosingSquareBracket = i;
                   break outer;
                }
                break;
             default:
          }
       }
       if (lastDot >= lastClosingSquareBracket) {
          if (value.startsWith('[')) lastDot--;
          return pathQuery.substring(0, lastDot + 1) + value
       } else {
          // It's possible that we've already added one suggestion
          for (let i = 0; i < pathSuggestions.length; ++i) {
             let sg = pathSuggestions[i]
             if (pathQuery.endsWith(sg)) {
                return pathQuery.substring(0, pathQuery.length - sg.length) + value
             }
          }
          return pathQuery.substring(0, lastClosingSquareBracket + 1) + value
       }
    }
    return (
        // <PageSection>
        <React.Fragment>
            <Card style={{ flexGrow: 1 }}>
                { !run && (<center><Spinner /></center>)}
                { run && (<>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between" }}>
                        <ToolbarSection aria-label="info">
                            <table className="pf-c-table pf-m-compact">
                                <tbody>
                                    <tr>
                                        <th>id</th>
                                        <th>test</th>
                                        <th>start</th>
                                        <th>stop</th>
                                        <th>Schema</th>
                                    </tr>
                                    <tr>
                                        <td>{run.id}</td>
                                        <td><NavLink to={`/test/${run.testId}`} >{run.testId}</NavLink></td>
                                        <td>{run.start ? DateTime.fromMillis(run.start).toFormat("yyyy-LL-dd HH:mm:ss ZZZ") : "--"}</td>
                                        <td>{run.stop ? DateTime.fromMillis(run.stop).toFormat("yyyy-LL-dd HH:mm:ss ZZZ") : "--"}</td>
                                        <td style={{ padding: "0" }}>
                                           { isTester &&
                                              <InputGroup>
                                                 <SchemaSelect value={schemaUri} onChange={setSchemaUri}/>
                                                 <Button onClick={ () => dispatch(actions.updateSchema(run.id, schemaUri)) }>Update</Button>
                                              </InputGroup>
                                           }
                                           { !isTester && <span>{schemaUri}</span> }
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </ToolbarSection>

                        <ToolbarSection aria-label="search" style={{ marginTop: 0 }}>
                            <InputGroup>
                                <Dropdown
                                    isOpen={pathTypeOpen}
                                    onSelect={(e) => { setPathType(e.currentTarget.id); setPathTypeOpen(false); }}
                                    toggle={
                                        <DropdownToggle onToggle={e => { setPathTypeOpen(e); }}>{pathType}</DropdownToggle>
                                    }
                                    dropdownItems={
                                        [
                                            <DropdownItem id="js" key="query">js</DropdownItem>,
                                            <DropdownItem id="jsonb_path_query_first" key="jsonb_path_query_first" isDisabled>jsonb_path_query_first</DropdownItem>,
                                            <DropdownItem id="jsonb_path_query_array" key="jsonb_path_query_array" isDisabled>jsonb_path_query_array</DropdownItem>,
                                        ]}
                                >
                                </Dropdown>
                                <Popover closeBtnAriaLabel="close jsonpath help"
                                         aria-label="jsonpath help"
                                         position="bottom"
                                         style={{ width : "500px " }}
                                         bodyContent={
                                       <div><p>The search expression is a JSONPath implemented in the browser.
                                            The syntax used in PostgreSQL JSONPath queries is partially transformed
                                            into JSONPath which has some limitations, though.</p>
                                            <p>Examples:</p>
                                            <code>$.store.book[0:2].title</code><br />
                                            <code>$.store.book[?(@.price &lt; 10)]</code><br />
                                            <code>$..*</code>
                                       </div>
                                    }>
                                    <Button variant={ButtonVariant.control} aria-label="show jsonpath help">
                                        <HelpIcon />
                                    </Button>
                                </Popover>
                                <Autosuggest inputProps={inputProps}
                                             suggestions={pathSuggestions}
                                             onSuggestionsFetchRequested={delayedUpdateSuggestions}
                                             onSuggestionsClearRequested={() => {
                                                if (pathQuery === "") setPathSuggestions([])
                                             }}
                                             getSuggestionValue={updateSuggestionValue}
                                             renderSuggestion={v => <div>{v}</div>}
                                             renderInputComponent={ inputProps => (
                                                 <input {...inputProps}
                                                        className="pf-c-form-control"
                                                        aria-label="jsonpath"
                                                        aria-invalid={pathInvalid}
                                                        style={{ width: "500px" }} />
                                               )}
                                             />
                                <Button variant={ButtonVariant.control} onClick={runPathQuery}>Find</Button>
                            </InputGroup>
                        </ToolbarSection>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    <Editor
                        value={data}
                        setValueGetter={e => { editor.current = e }}
                        options={{ mode: "application/ld+json" }}
                    />
                </CardBody>
                </>) }
            </Card>
        </React.Fragment>
        // </PageSection>        
    )
}