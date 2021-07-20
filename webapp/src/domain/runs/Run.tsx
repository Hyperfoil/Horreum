import React, { useState, useRef, useEffect } from 'react';
import { useParams } from "react-router"
import { useSelector, useDispatch } from 'react-redux'
import { Spinner, Bullseye } from '@patternfly/react-core';
import jsonpath from 'jsonpath';

import * as actions from './actions';
import * as selectors from './selectors';
import { RunsDispatch } from './reducers'
import { formatDateTime } from '../../utils'
import { useTester, rolesSelector } from '../../auth'
import { interleave } from '../../utils'
import { alertAction } from '../../alerts'

import Editor from '../../components/Editor/monaco/Editor';

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
    ToolbarContent,
    ToolbarItem,
} from '@patternfly/react-core';
import { EditIcon, HelpIcon } from '@patternfly/react-icons'
import { toString } from '../../components/Editor';
import { NavLink } from 'react-router-dom';
import Autosuggest, { InputProps, ChangeEvent, SuggestionsFetchRequestedParams } from 'react-autosuggest'
import { Description } from './components'
import ChangeSchemaModal from './ChangeSchemaModal'

function findFirstValue(o: any) {
   if (!o || Object.keys(o).length !== 1) {
      return undefined;
   }
   const key = Object.keys(o)[0]
   return { id: parseInt(key), schema: o[key] }
}

function getPaths(data: any) {
   if (!data) {
      return []
   }
   return Object.keys(data).filter(k => typeof data[k] === "object")
}

export default function Run() {
    const { id: stringId } = useParams<any>();
    const id = parseInt(stringId)
    const run = useSelector(selectors.get(id));
    const [data, setData] = useState(run ? toString(run.data) : "{}")

    const [pathQuery, setPathQuery] = useState("")
    const [pathInvalid, setPathInvalid] = useState(false)
    const [pathType, setPathType] = useState('js')
    const [pathTypeOpen, setPathTypeOpen] = useState(false)
    const [pathSuggestions, setPathSuggestions] = useState<string[]>([])

    const [changeSchemaModalOpen, setChangeSchemaModalOpen] = useState(false)
    const dispatch = useDispatch();
    const thunkDispatch = useDispatch<RunsDispatch>()
    const roles = useSelector(rolesSelector)
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get('token')
        dispatch(actions.get(id, token || undefined))
    }, [dispatch, id, roles])
    useEffect(() => {
        //change the loaded document when the run changes
        document.title = run && run.id ? "Run " + run.id + " | Horreum" : "Loading run... | Horreum"
        setData(run ? toString(run.data) : "{}");
    }, [run])

    const inputProps: InputProps<string> = {
        placeholder: "Enter selection path, e.g. $.foo[?(@.bar > 42)]",
        value: pathQuery,
        onChange: (evt: React.FormEvent<any>, v: ChangeEvent) => {
            // TODO
            const value = (v as any).newValue
            setPathInvalid(false)
            setPathQuery(value)
        },
        onKeyDown: evt => {
            if (evt.key === " " && evt.ctrlKey) {
                updateSuggestions(pathQuery);
            } else if (evt.key === "Enter") {
                runPathQuery();
            }
        }
    }
    const postgresTojsJsonPath = (query: string) => {
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
    const runPathQuery = () => {
        if (pathQuery === "") {
            setPathInvalid(false);
            setData(run ? toString(run.data) : "{}");
        } else {
            if (!run) {
               return;
            }
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
                  let obj: { [key: string]: string } = {}
                  var combinedPath = "";
                  path.forEach(x => {
                     if (combinedPath === "") {
                        combinedPath = String(x);
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
    const typingTimer = useRef<number | null>(null)
    const delayedUpdateSuggestions = ({value}: SuggestionsFetchRequestedParams) => {
       if (typingTimer.current !== null) {
          clearTimeout(typingTimer.current)
       }
       typingTimer.current = window.setTimeout(() => updateSuggestions(value), 1000)
    }
    const updateSuggestions = (value: string) => {
       if (!run) {
           return
       }
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
    const updateSuggestionValue = (value: string) => {
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
    const isTester = useTester((run && run.owner) || "")
    return (
        // <PageSection>
        <React.Fragment>
            <Card style={{ flexGrow: 1 }}>
                { !run && (<Bullseye><Spinner /></Bullseye>)}
                { run && (<>
                <CardHeader>
                    <Toolbar className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md" style={{ justifyContent: "space-between", width: "100%" }}>
                       <ToolbarContent style={{ width: "100%"}}>
                        <ToolbarItem aria-label="info"  style={{ width: "100%"}}>
                            <table className="pf-c-table pf-m-compact"  style={{ width: "100%"}}>
                                <tbody>
                                    <tr>
                                        <th>id</th>
                                        <th>test</th>
                                        <th>start</th>
                                        <th>stop</th>
                                        <th>description</th>
                                        <th>schema</th>
                                    </tr>
                                    <tr>
                                        <td>{run.id}</td>
                                        <td><NavLink to={`/test/${run.testid}`} >{run.testname || run.testid}</NavLink></td>
                                        <td>{ formatDateTime(run.start) }</td>
                                        <td>{ formatDateTime(run.stop) }</td>
                                        <td>{ Description(run.description) }</td>
                                        <td>{
                                          (run.schema && Object.keys(run.schema).length > 0 && interleave(Object.keys(run.schema).map(
                                             (key, i) => <NavLink key={2 * i} to={`/schema/${key}`}>{run.schema && run.schema[key] }</NavLink>
                                          ), i => <br key={2*i + 1} />)) || "--"}
                                          { isTester && <>
                                          <Button
                                             variant="link"
                                             style={{ float: 'right'}}
                                             onClick={ () => setChangeSchemaModalOpen(true) }
                                          ><EditIcon /></Button>
                                          <ChangeSchemaModal
                                             isOpen={ changeSchemaModalOpen }
                                             onClose={ () => setChangeSchemaModalOpen(false) }
                                             initialSchema={ findFirstValue(run.schema) }
                                             paths={ getPaths(run.data) }
                                             update={ (path, schema, schemaid) => thunkDispatch(actions.updateSchema(run.id, run.testid, path, schemaid, schema))
                                                .catch(e => dispatch(alertAction('SCHEME_UPDATE_FAILED', "Failed to update run schema", e))) }
                                          />
                                          </> }
                                       </td>
                                    </tr>
                                </tbody>
                            </table>
                        </ToolbarItem>

                        <ToolbarItem aria-label="search" style={{ marginTop: 0 }}>
                            <InputGroup>
                                <Dropdown
                                    isOpen={pathTypeOpen}
                                    onSelect={(e?: React.SyntheticEvent<HTMLDivElement>) => {
                                       if (e) {
                                          setPathType(e.currentTarget.id);
                                       }
                                       setPathTypeOpen(false);
                                    }}
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
                                         bodyContent={
                                       <div style={{ width : "500px " }}>
                                            <p>The search expression is a JSONPath implemented in the browser.
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
                                                 <input {...inputProps as any}
                                                        className="pf-c-form-control"
                                                        aria-label="jsonpath"
                                                        aria-invalid={pathInvalid}
                                                        style={{ width: "500px" }} />
                                               )}
                                             />
                                <Button variant={ButtonVariant.control} onClick={runPathQuery}>Find</Button>
                            </InputGroup>
                        </ToolbarItem>
                     </ToolbarContent>
                    </Toolbar>
                </CardHeader>
                <CardBody>
                    { !run.data && (<Bullseye><Spinner /></Bullseye>) }
                    { run.data &&
                    <Editor
                        value={data}
                        options={{
                           mode: "application/ld+json",
                           readOnly: true,
                        }}
                    />
                    }
                </CardBody>
                </>) }
            </Card>
        </React.Fragment>
        // </PageSection>
    )
}