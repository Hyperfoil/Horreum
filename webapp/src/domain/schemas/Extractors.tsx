import { useEffect, useMemo, useState } from 'react'
import { useDispatch } from 'react-redux'

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
} from '@patternfly/react-core'
import {
    Table,
    TableBody,
    TableHeader,
} from '@patternfly/react-table'
import {
    NavLink
} from 'react-router-dom'

import * as api from './api';
import { listBySchema, query } from '../runs/api'
import JsonPathDocsLink from '../../components/JsonPathDocsLink'
import Editor from '../../components/Editor/monaco/Editor'
import { Extractor } from '../../components/Accessors'
import { Run } from '../runs/reducers'
import { alertAction } from '../../alerts'

type TryJsonPathModalProps = {
    uri: string,
    jsonpath?: string,
    onChange(jsonpath: string): void,
    onClose(): void,
}

function TryJsonPathModal(props: TryJsonPathModalProps) {
    const [runs, setRuns] = useState<Run[]>()
    const [runCount, setRunCount] = useState(0) // total runs, not runs.length
    const [page, setPage] = useState(1)
    const [perPage, setPerPage] = useState(20)
    const [valid, setValid] = useState(true)
    const [result, setResult] = useState<string>()
    const [resultRunId, setResultRunId] = useState<number>()
    const pagination = useMemo(() => ({ page, perPage, sort: 'start', direction: 'descending' }),
        [ page, perPage])
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
                dispatch(alertAction('FETCH_RUNS_BY_URI', "Failed to fetch runs by Schema URI.", error))
                props.onClose()
            }
        )
    }, [props.uri, props.jsonpath, dispatch, props.onClose])
    const executeQuery = (runId: number) => {
        if (!props.jsonpath) {
            return "";
        }
        setResultRunId(runId)
        return query(runId, props.jsonpath, false, props.uri).then(result => {
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
         })
    }
    return (<Modal
        variant="large"
        title="Execute selected JsonPath on one of these runs"
        isOpen={props.jsonpath !== undefined}
        onClose={ () => {
            setRuns(undefined)
            setPage(1)
            setPerPage(20)
            setResult(undefined)
            setResultRunId(undefined)
            props.onClose()
        }}
    >
        <Flex>
            <FlexItem><JsonPathDocsLink /></FlexItem>
            <FlexItem grow={{ default: "grow" }}>
                <TextInput
                    id="jsonpath"
                    value={props.jsonpath || ""}
                    onChange={ value => {
                        setValid(true)
                        setResult(undefined)
                        props.onChange(value)
                    }}
                    validated={ valid ? 'default' : 'error' }
                />
            </FlexItem>
        </Flex>
        { runs === undefined && <Bullseye><Spinner size="xl"/></Bullseye> }
        { runs && result === undefined && <>
            <Table
                aria-label="Available runs"
                variant='compact'
                cells={ ['Test', 'Run', 'Description', '']}
                rows={ runs.map(r => ({ cells: [
                    r.testname,
                    { title: <NavLink to={ `/run/${r.id}?query=${ encodeURIComponent(props.jsonpath || "")}` }>{ r.id }</NavLink> },
                    r.description,
                    { title: <Button onClick={ () => executeQuery(r.id) }>Execute</Button> },
                ]}))}
            >
                <TableHeader />
                <TableBody />
            </Table>
            <Pagination
                itemCount={runCount}
                perPage={perPage}
                page={page}
                onSetPage={(e, p) => setPage(p)}
                onPerPageSelect={(e, pp) => setPerPage(pp)}
            />
        </>}
        { result !== undefined && <>
            <div style={{minHeight: "100px", height: "250px", resize: "vertical", overflow: "auto"}}>
                <Editor
                    value={ result}
                    options={{ readOnly: true }}
                />
            </div>
            <div style={{ textAlign: "right", paddingTop: "10px"}}>
                <Button
                    onClick={ () => {
                        setResult(undefined)
                        setResultRunId(undefined)
                    }}
                >Dismiss</Button>
                { '\u00A0' }
                <NavLink
                    className="pf-c-button pf-m-secondary"
                    to={ `/run/${resultRunId}?query=${ encodeURIComponent(props.jsonpath || "")}` }
                >
                    Go to run {resultRunId}
                </NavLink>
            </div>
        </>}
    </Modal>
    )
}


type ExtractorsProps = {
    uri: string,
    extractors: Extractor[]
    setExtractors(extractors: Extractor[]): void,
    isTester: boolean,
}

export default function Extractors(props: ExtractorsProps) {
    const [testExtractor, setTestExtractor] = useState<Extractor>()
    return (<>{ props.extractors.filter((e: Extractor) => !e.deleted).map((e: Extractor) =>
        <Flex style={{ marginBottom: "10px" }}>
            <FlexItem grow={{ default: "grow"}}>
                <Form isHorizontal={true} style={{ gridGap: "2px" }}>
                    <FormGroup label="Accessor" fieldId="accessor">
                        <TextInput
                            id="accessor"
                            value={e.newName || ""}
                            isReadOnly={!props.isTester}
                            onChange={newValue => {
                                e.newName = newValue
                                e.changed = true
                                props.setExtractors([...props.extractors])
                            }}/>
                    </FormGroup>
                    <FormGroup
                        label={
                            <>JSON path <JsonPathDocsLink /></>
                        }
                        fieldId="jsonpath"
                        validated={ !e.validationResult || e.validationResult.valid ? "default" : "error"}
                        helperTextInvalid={ e.validationResult?.reason || ""  }>
                        <TextInput
                            id="jsonpath"
                            value={e.jsonpath || ""}
                            isReadOnly={!props.isTester}
                            onChange={newValue => {
                                e.jsonpath = newValue;
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
            <FlexItem alignSelf={{ default: "alignSelfCenter"}}>
                <Button
                    variant="primary"
                    isDisabled={ !e.jsonpath }
                    onClick={ () => setTestExtractor(e) }
                >Try it!</Button>
            </FlexItem>
            { props.isTester &&
            <FlexItem alignSelf={{ default: "alignSelfCenter"}}>
                <Button
                    variant="secondary"
                    onClick={ () => {
                        e.deleted = true;
                        props.setExtractors([...props.extractors])
                    }}
                >
                    Delete
                </Button>
            </FlexItem>
            }
        </Flex>
    )}
        <TryJsonPathModal
            uri={props.uri}
            jsonpath={ testExtractor?.jsonpath }
            onChange={ jsonpath => {
                if (!testExtractor) {
                    return
                }
                testExtractor.jsonpath = jsonpath
                props.setExtractors([...props.extractors])
            } }
            onClose={ () => setTestExtractor(undefined) }
        />
    </>)
}