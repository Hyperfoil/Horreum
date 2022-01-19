import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"

import {
    Bullseye,
    Button,
    Flex,
    FlexItem,
    Form,
    FormGroup,
    Modal,
    Pagination,
    Spinner,
    TextInput,
} from "@patternfly/react-core"
import { Table, TableBody, TableHeader } from "@patternfly/react-table"
import { NavLink } from "react-router-dom"

import * as api from "./api"
import { Extractor } from "./api"
import { listBySchema, query } from "../runs/api"
import JsonPathDocsLink from "../../components/JsonPathDocsLink"
import Editor from "../../components/Editor/monaco/Editor"
import { checkAccessorName, INVALID_ACCESSOR_HELPER } from "../../components/Accessors"
import { Run } from "../runs/reducers"
import { alertAction } from "../../alerts"
import FindUsagesModal from "./FindUsagesModal"

type TryJsonPathModalProps = {
    uri: string
    jsonpath?: string
    onChange(jsonpath: string): void
    onClose(): void
}

function TryJsonPathModal(props: TryJsonPathModalProps) {
    const [runs, setRuns] = useState<Run[]>()
    const [runCount, setRunCount] = useState(0) // total runs, not runs.length
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [valid, setValid] = useState(true)
    const [result, setResult] = useState<string>()
    const [resultRunId, setResultRunId] = useState<number>()
    const pagination = useMemo(() => ({ page, perPage, sort: "start", direction: "Descending" }), [page, perPage])
    const dispatch = useDispatch()
    useEffect(() => {
        if (!props.jsonpath) {
            return
        }
        listBySchema(props.uri, pagination).then(
            response => {
                setRuns(response.runs)
                setRunCount(response.total)
            },
            error => {
                dispatch(alertAction("FETCH_RUNS_BY_URI", "Failed to fetch runs by Schema URI.", error))
                props.onClose()
            }
        )
    }, [props.uri, props.jsonpath, dispatch, props.onClose])
    const executeQuery = (runId: number) => {
        if (!props.jsonpath) {
            return ""
        }
        setResultRunId(runId)
        return query(runId, props.jsonpath, false, props.uri).then(
            result => {
                setValid(result.valid)
                if (result.valid) {
                    try {
                        result.value = JSON.parse(result.value)
                    } catch (e) {
                        // ignored
                    }
                    setResult(JSON.stringify(result.value, null, 2))
                } else {
                    setResult(result.reason)
                }
            },
            error => {
                setValid(false)
                setResult(error)
            }
        )
    }
    return (
        <Modal
            variant="large"
            title="Execute selected JsonPath on one of these runs"
            isOpen={props.jsonpath !== undefined}
            onClose={() => {
                setRuns(undefined)
                setPage(1)
                setPerPage(20)
                setResult(undefined)
                setResultRunId(undefined)
                props.onClose()
            }}
        >
            <Flex>
                <FlexItem>
                    <JsonPathDocsLink />
                </FlexItem>
                <FlexItem grow={{ default: "grow" }}>
                    <TextInput
                        id="jsonpath"
                        value={props.jsonpath || ""}
                        onChange={value => {
                            setValid(true)
                            setResult(undefined)
                            props.onChange(value)
                        }}
                        validated={valid ? "default" : "error"}
                    />
                </FlexItem>
            </Flex>
            {runs === undefined && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {runs && result === undefined && (
                <>
                    {/* TODO FIXME */}
                    <div style={{ display: "block", overflowY: "scroll", maxHeight: "50vh" }}>
                        <Table
                            aria-label="Available runs"
                            variant="compact"
                            cells={["Test", "Run", "Description", ""]}
                            rows={runs.map(r => ({
                                cells: [
                                    r.testname,
                                    {
                                        title: (
                                            <NavLink
                                                to={`/run/${r.id}?query=${encodeURIComponent(props.jsonpath || "")}`}
                                            >
                                                {r.id}
                                            </NavLink>
                                        ),
                                    },
                                    r.description,
                                    { title: <Button onClick={() => executeQuery(r.id)}>Execute</Button> },
                                ],
                            }))}
                        >
                            <TableHeader />
                            <TableBody />
                        </Table>
                    </div>
                    <Pagination
                        itemCount={runCount}
                        perPage={perPage}
                        page={page}
                        onSetPage={(e, p) => setPage(p)}
                        onPerPageSelect={(e, pp) => setPerPage(pp)}
                    />
                </>
            )}
            {result !== undefined && (
                <>
                    <div style={{ minHeight: "100px", height: "250px", resize: "vertical", overflow: "auto" }}>
                        <Editor value={result} options={{ readOnly: true }} />
                    </div>
                    <div style={{ textAlign: "right", paddingTop: "10px" }}>
                        <Button
                            onClick={() => {
                                setResult(undefined)
                                setResultRunId(undefined)
                            }}
                        >
                            Dismiss
                        </Button>
                        {"\u00A0"}
                        <NavLink
                            className="pf-c-button pf-m-secondary"
                            to={`/run/${resultRunId}?query=${encodeURIComponent(props.jsonpath || "")}`}
                        >
                            Go to run {resultRunId}
                        </NavLink>
                    </div>
                </>
            )}
        </Modal>
    )
}

type RenameModalProps = {
    extractor?: Extractor
    onRename(accessor: string): void
    onClose(): void
}

function RenameModal(props: RenameModalProps) {
    const [name, setName] = useState("")
    const valid = checkAccessorName(name)
    useEffect(() => {
        if (props.extractor) {
            setName(props.extractor.accessor)
        }
    }, [props.extractor])
    return (
        <Modal
            isOpen={!!props.extractor}
            onClose={props.onClose}
            title={`Rename accessor ${props.extractor?.accessor}`}
            actions={[
                <Button
                    key="rename"
                    onClick={() => {
                        props.onRename(name)
                        props.onClose()
                    }}
                >
                    Rename
                </Button>,
                <Button key="cancel" variant="secondary" onClick={() => props.onClose()}>
                    Cancel
                </Button>,
            ]}
        >
            When the accessor is renamed the old name is deprecated; some places may still use that and we don't want to
            break the usages. However the old name is not displayed anymore by default.
            <TextInput aria-label="rename" value={name} onChange={setName} validated={valid ? "default" : "warning"} />
            {!valid && INVALID_ACCESSOR_HELPER}
        </Modal>
    )
}

type ExtractorsProps = {
    uri: string
    extractors: Extractor[]
    setExtractors(extractors: Extractor[]): void
    isTester: boolean
}

export default function Extractors(props: ExtractorsProps) {
    const [testExtractor, setTestExtractor] = useState<Extractor>()
    const [findUsages, setFindUsages] = useState<Extractor>()
    const [rename, setRename] = useState<Extractor>()
    return (
        <>
            {props.extractors
                .filter((e: Extractor) => !e.deleted)
                .map((e: Extractor, i) => {
                    const accessorValid = checkAccessorName(e.accessor)
                    return (
                        <Flex key={i} style={{ marginBottom: "10px" }} alignItems={{ default: "alignItemsCenter" }}>
                            <FlexItem grow={{ default: "grow" }}>
                                <Form isHorizontal={true} style={{ gridGap: "2px" }}>
                                    <FormGroup
                                        label="Accessor"
                                        fieldId="accessor"
                                        validated={accessorValid ? "default" : "warning"}
                                        helperText={accessorValid ? null : INVALID_ACCESSOR_HELPER}
                                    >
                                        <TextInput
                                            id="accessor"
                                            value={e.oldName ? e.oldName + " \u279E " + e.accessor : e.accessor}
                                            isReadOnly={!props.isTester || e.id >= 0}
                                            validated={accessorValid ? "default" : "warning"}
                                            onChange={newValue => {
                                                e.accessor = newValue
                                                e.changed = true
                                                props.setExtractors([...props.extractors])
                                            }}
                                        />
                                    </FormGroup>
                                    <FormGroup
                                        label={
                                            <>
                                                JSON path <JsonPathDocsLink />
                                            </>
                                        }
                                        fieldId="jsonpath"
                                        validated={
                                            !e.validationResult || e.validationResult.valid ? "default" : "error"
                                        }
                                        helperTextInvalid={e.validationResult?.reason || ""}
                                    >
                                        <TextInput
                                            id="jsonpath"
                                            value={e.jsonpath || ""}
                                            isReadOnly={!props.isTester}
                                            onChange={newValue => {
                                                e.jsonpath = newValue
                                                e.changed = true
                                                e.validationResult = undefined
                                                props.setExtractors([...props.extractors])
                                                if (e.validationTimer) {
                                                    clearTimeout(e.validationTimer)
                                                }
                                                e.validationTimer = window.setTimeout(() => {
                                                    if (e.jsonpath) {
                                                        api.testJsonPath(e.jsonpath).then(result => {
                                                            e.validationResult = result
                                                            props.setExtractors([...props.extractors])
                                                        })
                                                    }
                                                }, 1000)
                                            }}
                                        />
                                    </FormGroup>
                                </Form>
                            </FlexItem>
                            {props.isTester && (
                                <>
                                    <FlexItem>
                                        <Button
                                            variant="primary"
                                            isDisabled={!e.jsonpath}
                                            onClick={() => setTestExtractor(e)}
                                        >
                                            Try it!
                                        </Button>
                                    </FlexItem>
                                    <FlexItem>
                                        <Button
                                            variant="secondary"
                                            onClick={() => setFindUsages(e)}
                                            isDisabled={e.id < 0}
                                        >
                                            Find usages
                                        </Button>
                                    </FlexItem>
                                    <FlexItem>
                                        <Button variant="secondary" onClick={() => setRename(e)} isDisabled={e.id < 0}>
                                            Rename
                                        </Button>
                                    </FlexItem>
                                    <FlexItem>
                                        <Button
                                            variant="danger"
                                            onClick={() => {
                                                if (e.id < 0) {
                                                    props.setExtractors([...props.extractors.filter(ex => ex !== e)])
                                                } else {
                                                    e.deleted = true
                                                    props.setExtractors([...props.extractors])
                                                }
                                            }}
                                        >
                                            Delete
                                        </Button>
                                    </FlexItem>
                                </>
                            )}
                        </Flex>
                    )
                })}
            <TryJsonPathModal
                uri={props.uri}
                jsonpath={testExtractor?.jsonpath}
                onChange={jsonpath => {
                    if (!testExtractor) {
                        return
                    }
                    testExtractor.jsonpath = jsonpath
                    props.setExtractors([...props.extractors])
                }}
                onClose={() => setTestExtractor(undefined)}
            />
            <FindUsagesModal
                extractorId={findUsages?.id || -1}
                accessor={findUsages?.accessor}
                onClose={() => setFindUsages(undefined)}
            />
            <RenameModal
                extractor={rename}
                onRename={name => {
                    if (!rename) {
                        return //shouldn't happen
                    }
                    if (!rename.oldName) {
                        rename.oldName = rename.accessor
                    }
                    rename.accessor = name
                    rename.changed = true
                    props.setExtractors([...props.extractors])
                    console.log(props.extractors)
                }}
                onClose={() => setRename(undefined)}
            />
        </>
    )
}
