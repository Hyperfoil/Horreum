import React, {useContext, useEffect, useRef, useState} from "react"
import { useHistory } from "react-router-dom"
import jsonpath from "jsonpath"

import {
    Button,
    ButtonVariant,
    Dropdown,
    DropdownToggle,
    DropdownItem,
    InputGroup,
    Popover,
    Toolbar,
    ToolbarContent,
    ToolbarItem,
} from "@patternfly/react-core"
import { HelpIcon } from "@patternfly/react-icons"
import { toString } from "../../components/Editor"
import Autosuggest, { InputProps, ChangeEvent, SuggestionsFetchRequestedParams } from "react-autosuggest"
import { QueryResult } from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";

type ToolbarProps = {
    originalData: any
    onRemoteQuery: (query: string, array: boolean) => Promise<QueryResult>
    onDataUpdate(newData: string): void
}

export default function JsonPathSearchToolbar(props: ToolbarProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [pathQuery, setPathQuery] = useState("")
    const [pathInvalid, setPathInvalid] = useState(false)
    const [pathType, setPathType] = useState("jsonb_path_query_first")
    const [pathTypeOpen, setPathTypeOpen] = useState(false)
    const [pathSuggestions, setPathSuggestions] = useState<string[]>([])

    function runPathQuery(type?: string, query?: string) {
        execQuery(props.originalData, type || pathType, query || pathQuery, props.onRemoteQuery).then(
            ([result, valid]) => {
                props.onDataUpdate(result)
                setPathInvalid(!valid)
            },
            error => {
                alerting.dispatchError(error, "QUERY_ERROR", "Failed to execute query!")
            }
        )
    }

    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search)
        const query = urlParams.get("query")
        const type = urlParams.get("type")
        if (type) {
            setPathType(type)
        }
        if (query) {
            setPathQuery(query)
            if (props.originalData) {
                runPathQuery(type || undefined, query)
            }
        }
    }, [props.originalData])

    const history = useHistory()
    function onQueryUpdate(type: string, query: string) {
        const loc = window.location
        const urlParams = new URLSearchParams(window.location.search)
        urlParams.set("type", type)
        if (query) {
            urlParams.set("query", query)
        } else {
            urlParams.delete("query")
        }
        history.replace(`${loc.pathname}?${urlParams.toString()}${loc.hash}`)
    }

    const inputProps: InputProps<string> = {
        placeholder: "Enter selection path, e.g. $.foo[?(@.bar > 42)]",
        value: pathQuery,
        onChange: (evt: React.FormEvent<any>, v: ChangeEvent) => {
            // TODO
            const value = (v as any).newValue
            setPathInvalid(false)
            setPathQuery(value)
            onQueryUpdate(pathType, value)
        },
        onKeyDown: evt => {
            if (evt.key === " " && evt.ctrlKey) {
                updateSuggestions()
            } else if (evt.key === "Enter") {
                runPathQuery()
            }
        },
    }
    const updateSuggestions = () => {
        const [suggs, valid] = findSuggestions(props.originalData, pathQuery)
        setPathSuggestions(suggs)
        if (valid !== undefined) {
            setPathInvalid(!valid)
        }
    }
    const typingTimer = useRef<number | null>(null)
    const delayedUpdateSuggestions = (_: SuggestionsFetchRequestedParams) => {
        if (typingTimer.current !== null) {
            clearTimeout(typingTimer.current)
        }
        typingTimer.current = window.setTimeout(updateSuggestions, 1000)
    }

    return (
        <Toolbar
            className="pf-l-toolbar pf-u-justify-content-space-between pf-u-mx-xl pf-u-my-md"
            style={{ justifyContent: "space-between", width: "100%" }}
        >
            <ToolbarContent style={{ width: "100%" }}>
                <ToolbarItem aria-label="search" style={{ marginTop: 0 }}>
                    <InputGroup>
                        <Dropdown
                            isOpen={pathTypeOpen}
                            onSelect={(e?: React.SyntheticEvent<HTMLDivElement>) => {
                                if (e) {
                                    setPathType(e.currentTarget.id)
                                    onQueryUpdate(e.currentTarget.id, pathQuery)
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
                                <DropdownItem id="jsonb_path_query_first" key="jsonb_path_query_first">
                                    jsonb_path_query_first
                                </DropdownItem>,
                                <DropdownItem id="jsonb_path_query_array" key="jsonb_path_query_array">
                                    jsonb_path_query_array
                                </DropdownItem>,
                                <DropdownItem id="js" key="query">
                                    js
                                </DropdownItem>
                            ]}
                        ></Dropdown>
                        <SearchQueryHelp pathType={pathType} />
                        <Autosuggest
                            inputProps={inputProps}
                            suggestions={pathSuggestions}
                            onSuggestionsFetchRequested={delayedUpdateSuggestions}
                            onSuggestionsClearRequested={() => {
                                if (pathQuery === "") setPathSuggestions([])
                            }}
                            getSuggestionValue={value => updateSuggestionValue(value, pathQuery, pathSuggestions)}
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
                        <Button variant="control" onClick={() => runPathQuery()}>
                            Find
                        </Button>
                        <Button
                            variant="control"
                            onClick={() => {
                                setPathQuery("")
                                setPathInvalid(false)
                                onQueryUpdate(pathType, "")
                                props.onDataUpdate(toString(props.originalData))
                            }}
                        >
                            Clear
                        </Button>
                    </InputGroup>
                </ToolbarItem>
            </ToolbarContent>
        </Toolbar>
    )
}

function execQuery(
    data: any,
    type: string,
    query: string,
    remote: (q: string, array: boolean) => Promise<QueryResult>
): Promise<[string, boolean]> {
    if (!data) {
        return Promise.resolve(["", true])
    }
    if (query === "") {
        return Promise.resolve([toString(data), true])
    }
    let array = false
    switch (type) {
        case "js":
            return Promise.resolve(execQueryLocal(data, query))
        case "jsonb_path_query_first":
            break
        case "jsonb_path_query_array":
            array = true
            break
        default:
            return Promise.reject("Unknown type of query")
    }
    return remote(query, array).then(result => {
        if (result.valid) {
            try {
                result.value = result.value ? JSON.parse(result.value) : undefined
            } catch (e) {
                // ignored
            }
            result.value = JSON.stringify(result.value, null, 2)
            return [result.value || "", true]
        } else {
            return [result.reason || "", false]
        }
    })
}

function execQueryLocal(data: any, pathQuery: string): [string, boolean] {
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
        const found = jsonpath.nodes(data, query).map(({ path, value }) => {
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

function findSuggestions(data: any, value: string): [string[], boolean | undefined] {
    if (!data) {
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
            .paths(data, query)
            .map(path => path[path.length - 1].toString())
            .filter(k => k.startsWith(incomplete))
            .map(k => (k.match(/^[a-zA-Z0-9_]*$/) ? k : '"' + k + '"'))
        return [[...new Set(sgs)].sort(), undefined]
    } catch (e) {
        console.log("Failed query: " + query)
        return [[], false]
    }
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

type SearchQueryHelpProps = {
    pathType: string
}

function SearchQueryHelp({ pathType }: SearchQueryHelpProps) {
    return (
        <Popover
            closeBtnAriaLabel="close jsonpath help"
            aria-label="jsonpath help"
            position="bottom"
            hasAutoWidth={true}
            bodyContent={
                <div style={{ width: "450px" }}>
                    {pathType == "js" ? (
                        <>
                            <p>
                                Variant <code>js</code> executes the search expression inside your browser using{" "}
                                <a
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    href="https://www.npmjs.com/package/jsonpath"
                                >
                                    Node.js JSONPath implementation
                                </a>
                                . While this won't help you much formulating the PostgreSQL queries (most Horreum
                                operations are based on PostgreSQL flavour of JSONPath) this implementation will return
                                the paths to the matching nodes, too.
                            </p>
                            <p>Examples:</p>
                            <code>$.store.book[0:2].title</code>
                            <br />
                            <code>$.store.book[?(@.price &lt; 10)]</code>
                            <br />
                            <code>$..*</code>
                        </>
                    ) : (
                        <p>
                            Both <code>jsonb_path_query_first</code> and <code>jsonb_path_query_array</code> use{" "}
                            <a
                                target="_blank"
                                rel="noopener noreferrer"
                                href="https://www.postgresql.org/docs/12/functions-json.html#FUNCTIONS-SQLJSON-PATH"
                            >
                                PostgreSQL flavour
                            </a>{" "}
                            of JSONPath. The former returns only the first match while the latter returns all matching
                            nodes.
                        </p>
                    )}
                </div>
            }
        >
            <Button variant={ButtonVariant.control} aria-label="show jsonpath help" style={{ maxHeight: "36px" }}>
                <HelpIcon />
            </Button>
        </Popover>
    )
}
