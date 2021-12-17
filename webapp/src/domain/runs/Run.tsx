import React, { useState, useRef, useEffect } from "react"
import { useParams } from "react-router"
import { useSelector, useDispatch } from "react-redux"
import { Spinner, Bullseye } from "@patternfly/react-core"
import jsonpath from "jsonpath"

import * as actions from "./actions"
import * as selectors from "./selectors"
import * as api from "./api"
import { Run as RunDef, RunsDispatch } from "./reducers"
import { formatDateTime, noop } from "../../utils"
import { useTester, teamsSelector } from "../../auth"
import { interleave } from "../../utils"
import { alertAction } from "../../alerts"

import Editor from "../../components/Editor/monaco/Editor"

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
    PageSection,
    Popover,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
} from "@patternfly/react-core"
import { EditIcon, HelpIcon } from "@patternfly/react-icons"
import { toString } from "../../components/Editor"
import { NavLink } from "react-router-dom"
import Autosuggest, { InputProps, ChangeEvent, SuggestionsFetchRequestedParams } from "react-autosuggest"
import { Description } from "./components"
import ChangeSchemaModal from "./ChangeSchemaModal"

function findFirstValue(o: any) {
    if (!o || Object.keys(o).length !== 1) {
        return undefined
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

function postgresTojsJsonPath(query: string) {
    query = query.replace(/\.\*\*\.?/g, "..")
    query = query.replace(/\."([^"]*)"/g, "['$1']")
    query = query.replace(/ *\? */, "?")

    // Note: postgres query allows conditions beyond selecting array items
    const qIndex = query.indexOf("?")
    if (qIndex >= 0) {
        query = query.substring(0, qIndex) + "[" + query.substring(qIndex) + "]"
    }
    return query
}

function execQuery(run: RunDef | false, type: string, query: string): Promise<[string, boolean]> {
    if (!run) {
        return Promise.resolve(["", true])
    }
    if (query === "") {
        return Promise.resolve([toString(run.data), true])
    }
    let array = false
    switch (type) {
        case "js":
            return Promise.resolve(execQueryLocal(run, query))
        case "jsonb_path_query_first":
            break
        case "jsonb_path_query_array":
            array = true
            break
        default:
            return Promise.reject("Unknown type of query")
    }
    return api.query(run.id, query, array).then(result => {
        if (result.valid) {
            try {
                result.value = JSON.parse(result.value)
            } catch (e) {
                // ignored
            }
            result.value = JSON.stringify(result.value, null, 2)
            return [result.value, true]
        } else {
            return [result.reason, false]
        }
    })
}

function execQueryLocal(run: RunDef, pathQuery: string): [string, boolean] {
    let query = postgresTojsJsonPath(pathQuery)
    while (query.endsWith(".")) {
        query = query.substring(0, query.length - 1)
    }
    if (query.startsWith("@")) {
        query = "$..*[?(" + query + ")]"
    } else if (!query.startsWith("$")) {
        query = "$.." + query
    }
    try {
        const found = jsonpath.nodes(run.data, query).map(({ path, value }) => {
            const obj: { [key: string]: string } = {}
            let combinedPath = ""
            path.forEach(x => {
                if (combinedPath === "") {
                    combinedPath = String(x)
                } else {
                    if (typeof x === "number") {
                        combinedPath = combinedPath + "[" + x + "]"
                    } else if (x.match(/^[a-zA-Z0-9_]*$/)) {
                        combinedPath = combinedPath + "." + x
                    } else {
                        combinedPath = combinedPath + '."' + x.replace(/"/g, '\\"') + '"'
                    }
                }
            })
            obj[combinedPath] = value
            return obj
        })
        return [JSON.stringify(found, null, 2), true]
    } catch (e) {
        console.log("Failed query: " + query)
        return [(e as any).message, false]
    }
}

function updateSuggestionValue(value: string, pathQuery: string, pathSuggestions: string[]) {
    let quoted = false
    let lastDot = 0
    let lastClosingSquareBracket = 0
    outer: for (let i = pathQuery.length; i >= 0; --i) {
        switch (pathQuery.charAt(i)) {
            // we're not handling escaped quotes...
            case '"':
                quoted = !quoted
                break
            case ".":
                if (!quoted) {
                    lastDot = i
                    break outer
                }
                break
            case "]":
                if (!quoted) {
                    lastClosingSquareBracket = i
                    break outer
                }
                break
            default:
        }
    }
    if (lastDot >= lastClosingSquareBracket) {
        if (value.startsWith("[")) lastDot--
        return pathQuery.substring(0, lastDot + 1) + value
    } else {
        // It's possible that we've already added one suggestion
        for (let i = 0; i < pathSuggestions.length; ++i) {
            const sg = pathSuggestions[i]
            if (pathQuery.endsWith(sg)) {
                return pathQuery.substring(0, pathQuery.length - sg.length) + value
            }
        }
        return pathQuery.substring(0, lastClosingSquareBracket + 1) + value
    }
}

function findSuggestions(run: RunDef | false, value: string): [string[], boolean | undefined] {
    if (!run) {
        return [[], true]
    }
    let query = postgresTojsJsonPath(value.trim())
    const conditionStart = query.indexOf("@")
    if (conditionStart >= 0) {
        let condition = query.substring(conditionStart + 1)
        const conditionEnd = Math.min(
            ...["<", ">", "!=", "==", " ", ")"].map(op => {
                const opIndex = condition.indexOf(op)
                return opIndex >= 0 ? opIndex : condition.length
            })
        )
        condition = condition.substring(0, conditionEnd)
        const qIndex = query.indexOf("?")
        if (qIndex > 0) {
            // condition start looks like [?(@...
            query = query.substring(0, qIndex - 1).trim() + condition
        } else {
            query = query.substring(0, conditionStart) + condition
        }
    }
    const lastDot = Math.max(query.lastIndexOf("."), query.lastIndexOf("]"))
    let incomplete = ""
    if (lastDot >= 0) {
        incomplete = query.substring(lastDot + 1)
        if (incomplete === "*") {
            incomplete = ""
        }
        query = query.substring(0, lastDot + 1)
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
        return [[], undefined]
    } else {
        incomplete = query
        query = "$..*"
    }
    try {
        const sgs = jsonpath
            .paths(run.data, query)
            .map(path => path[path.length - 1].toString())
            .filter(k => k.startsWith(incomplete))
            .map(k => (k.match(/^[a-zA-Z0-9_]*$/) ? k : '"' + k + '"'))
        return [[...new Set(sgs)].sort(), undefined]
    } catch (e) {
        console.log("Failed query: " + query)
        return [[], false]
    }
}

export default function Run() {
    const { id: stringId } = useParams<any>()
    const id = parseInt(stringId)
    const run = useSelector(selectors.get(id))
    const [data, setData] = useState(run ? toString(run.data) : "{}")
    const [pathQuery, setPathQuery] = useState("")
    const [pathInvalid, setPathInvalid] = useState(false)
    const [pathType, setPathType] = useState("js")
    const [pathTypeOpen, setPathTypeOpen] = useState(false)
    const [pathSuggestions, setPathSuggestions] = useState<string[]>([])

    const [changeSchemaModalOpen, setChangeSchemaModalOpen] = useState(false)
    const dispatch = useDispatch<RunsDispatch>()
    const teams = useSelector(teamsSelector)

    const runPathQuery = () => {
        execQuery(run, pathType, pathQuery).then(
            ([result, valid]) => {
                setData(result)
                setPathInvalid(!valid)
            },
            error => {
                dispatch(alertAction("QUERY_ERROR", "Failed to execute query!", error))
            }
        )
    }
    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const token = urlParams.get("token")
        dispatch(actions.get(id, token || undefined)).catch(noop)
        const query = urlParams.get("query")
        if (query) {
            setPathQuery(query)
            setPathType("jsonb_path_query_first")
            // we won't run the query automatically since there would be a race between loading
            // the data and executing the query and we would have to deal with ordering
        }
    }, [dispatch, id, teams])
    useEffect(() => {
        //change the loaded document when the run changes
        document.title = run && run.id ? "Run " + run.id + " | Horreum" : "Loading run... | Horreum"
        setData(run ? toString(run.data) : "{}")
    }, [run])
    const updateSuggestions = () => {
        const [suggs, valid] = findSuggestions(run, pathQuery)
        setPathSuggestions(suggs)
        if (valid !== undefined) {
            setPathInvalid(!valid)
        }
    }
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
                updateSuggestions()
            } else if (evt.key === "Enter") {
                runPathQuery()
            }
        },
    }
    const typingTimer = useRef<number | null>(null)
    const delayedUpdateSuggestions = (_: SuggestionsFetchRequestedParams) => {
        if (typingTimer.current !== null) {
            clearTimeout(typingTimer.current)
        }
        typingTimer.current = window.setTimeout(updateSuggestions, 1000)
    }

    const isTester = useTester((run && run.owner) || "")
    return (
        <PageSection>
            {!run && (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            )}
            {run && (
                <Card style={{ height: "100%" }}>
                    <CardHeader>
                        <Toolbar
                            className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
                            style={{ justifyContent: "space-between", width: "100%" }}
                        >
                            <ToolbarContent style={{ width: "100%" }}>
                                <ToolbarItem aria-label="info" style={{ width: "100%" }}>
                                    <table className="pf-c-table pf-m-compact" style={{ width: "100%" }}>
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
                                                <td>
                                                    <NavLink to={`/test/${run.testid}`}>
                                                        {run.testname || run.testid}
                                                    </NavLink>
                                                </td>
                                                <td>{formatDateTime(run.start)}</td>
                                                <td>{formatDateTime(run.stop)}</td>
                                                <td>{Description(run.description)}</td>
                                                <td>
                                                    {(run.schema &&
                                                        Object.keys(run.schema).length > 0 &&
                                                        interleave(
                                                            Object.keys(run.schema).map((key, i) => (
                                                                <NavLink key={2 * i} to={`/schema/${key}`}>
                                                                    {run.schema && run.schema[key]}
                                                                </NavLink>
                                                            )),
                                                            i => <br key={2 * i + 1} />
                                                        )) ||
                                                        "--"}
                                                    {isTester && (
                                                        <>
                                                            <Button
                                                                variant="link"
                                                                style={{ float: "right" }}
                                                                onClick={() => setChangeSchemaModalOpen(true)}
                                                            >
                                                                <EditIcon />
                                                            </Button>
                                                            <ChangeSchemaModal
                                                                isOpen={changeSchemaModalOpen}
                                                                onClose={() => setChangeSchemaModalOpen(false)}
                                                                initialSchema={findFirstValue(run.schema)}
                                                                paths={getPaths(run.data)}
                                                                update={(path, schema, schemaid) =>
                                                                    dispatch(
                                                                        actions.updateSchema(
                                                                            run.id,
                                                                            run.testid,
                                                                            path,
                                                                            schemaid,
                                                                            schema
                                                                        )
                                                                    ).catch(noop)
                                                                }
                                                            />
                                                        </>
                                                    )}
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
                                                    setPathType(e.currentTarget.id)
                                                }
                                                setPathTypeOpen(false)
                                            }}
                                            toggle={
                                                <DropdownToggle
                                                    onToggle={e => {
                                                        setPathTypeOpen(e)
                                                    }}
                                                >
                                                    {pathType}
                                                </DropdownToggle>
                                            }
                                            dropdownItems={[
                                                <DropdownItem id="js" key="query">
                                                    js
                                                </DropdownItem>,
                                                <DropdownItem id="jsonb_path_query_first" key="jsonb_path_query_first">
                                                    jsonb_path_query_first
                                                </DropdownItem>,
                                                <DropdownItem id="jsonb_path_query_array" key="jsonb_path_query_array">
                                                    jsonb_path_query_array
                                                </DropdownItem>,
                                            ]}
                                        ></Dropdown>
                                        <Popover
                                            closeBtnAriaLabel="close jsonpath help"
                                            aria-label="jsonpath help"
                                            position="bottom"
                                            bodyContent={
                                                <div style={{ width: "500px " }}>
                                                    <p>
                                                        The search expression is a JSONPath implemented in the browser.
                                                        The syntax used in PostgreSQL JSONPath queries is partially
                                                        transformed into JSONPath which has some limitations, though.
                                                    </p>
                                                    <p>Examples:</p>
                                                    <code>$.store.book[0:2].title</code>
                                                    <br />
                                                    <code>$.store.book[?(@.price &lt; 10)]</code>
                                                    <br />
                                                    <code>$..*</code>
                                                </div>
                                            }
                                        >
                                            <Button variant={ButtonVariant.control} aria-label="show jsonpath help">
                                                <HelpIcon />
                                            </Button>
                                        </Popover>
                                        <Autosuggest
                                            inputProps={inputProps}
                                            suggestions={pathSuggestions}
                                            onSuggestionsFetchRequested={delayedUpdateSuggestions}
                                            onSuggestionsClearRequested={() => {
                                                if (pathQuery === "") setPathSuggestions([])
                                            }}
                                            getSuggestionValue={value =>
                                                updateSuggestionValue(value, pathQuery, pathSuggestions)
                                            }
                                            renderSuggestion={v => <div>{v}</div>}
                                            renderInputComponent={inputProps => (
                                                <input
                                                    {...(inputProps as any)}
                                                    className="pf-c-form-control"
                                                    aria-label="jsonpath"
                                                    aria-invalid={pathInvalid}
                                                    style={{ width: "500px" }}
                                                />
                                            )}
                                        />
                                        <Button variant="control" onClick={runPathQuery}>
                                            Find
                                        </Button>
                                        <Button
                                            variant="control"
                                            onClick={() => {
                                                setPathQuery("")
                                                setData(toString(run.data))
                                            }}
                                        >
                                            Clear
                                        </Button>
                                    </InputGroup>
                                </ToolbarItem>
                            </ToolbarContent>
                        </Toolbar>
                    </CardHeader>
                    <CardBody>
                        {!run.data && (
                            <Bullseye>
                                <Spinner />
                            </Bullseye>
                        )}
                        {run.data && (
                            <Editor
                                value={data}
                                options={{
                                    mode: "application/ld+json",
                                    readOnly: true,
                                }}
                            />
                        )}
                    </CardBody>
                </Card>
            )}
        </PageSection>
    )
}
