import React, { useState, useEffect, useMemo } from 'react'
import {  useDispatch } from 'react-redux'
import { fetchDashboard, fetchTags } from './api'
import { Panel } from './types'
import { ChangesTabs } from './Changes'
import { alertAction } from '../../alerts'
import TestSelect, { SelectedTest } from '../../components/TestSelect'
import PanelChart from './PanelChart'

import {
    Bullseye,
    Button,
    Card,
    CardBody,
    CardHeader,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    EmptyState,
    EmptyStateBody,
    Select,
    SelectOption,
    SelectOptionObject,
    Title,
} from '@patternfly/react-core';
import { NavLink, useHistory } from 'react-router-dom';

function convertTags(tags: any): string {
    if (!tags) {
        return "<no tags>"
    }
    let str = ""
    for (let [key, value] of Object.entries(tags)) {
        if (str !== "") {
            str = str + ";"
        }
        if (typeof value === "object") {
            // Use the same format as Postgres
            value = JSON.stringify(value).replaceAll(",", ", ").replaceAll(":", ": ");
        }
        str = str + key + ":" + value
    }
    return str
}

type TimespanSelectProps = {
    onChange(span: number): void,
}

type Timespan = SelectOptionObject & {
    seconds: number
}

function makeTimespan(title: string, seconds: number): Timespan {
    return { seconds, toString: () => title }
}

const TimespanSelect = (props: TimespanSelectProps) => {
    const [isExpanded, setExpanded] = useState(false)
    const options = useMemo(() => [
        makeTimespan("all", 2000000000),
        makeTimespan("1 year", 366 * 86400),
        makeTimespan("6 months", 183 * 86400),
        makeTimespan("3 months", 92 * 86400),
        makeTimespan("1 month", 31 * 86400),
        makeTimespan("1 week", 7 * 86400),
        makeTimespan("1 day", 86400),
        makeTimespan("6 hours", 6 * 3600),
    ], [])
    const [selected, setSelected] = useState(options[4])
    return (<Select
         isOpen={isExpanded}
         selections={selected}
         onToggle={setExpanded}
         onSelect={(_, value) => {
             const timespan = value as Timespan
             setSelected(timespan)
             setExpanded(false)
             props.onChange(timespan.seconds)
         }}>
        { options.map((timespan, i)=> <SelectOption key={i} value={timespan}>{timespan.toString()}</SelectOption>) }
    </Select>)
}

type LineTypeSelectProps = {
    onChange(type: string): void
}

type LineType = SelectOptionObject & {
    type: string,
}

function makeLineType(title: string, type: string): LineType {
    return { type, toString: () => title }
}

const LineTypeSelect = (props: LineTypeSelectProps) => {
    const [isExpanded, setExpanded] = useState(false)
    const options = useMemo(() => [
        makeLineType("steps", "stepAfter"),
        makeLineType("straight", "linear"),
        makeLineType("curve", "monotone"),
    ], [])
    const [selected, setSelected] = useState(options[1])
    return (<Select
         isOpen={isExpanded}
         selections={selected}
         onToggle={setExpanded}
         onSelect={(_, value) => {
             const linetype = value as LineType
             setSelected(linetype)
             setExpanded(false)
             props.onChange(linetype.type)
         }}>
        { options.map((timespan, i)=> <SelectOption key={i} value={timespan}>{timespan.toString()}</SelectOption>) }
    </Select>)
}

export default () => {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    const test = params.get("test")
    const tags = params.get("tags")
    const dispatch = useDispatch()
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [dashboardUrl, setDashboardUrl] = useState("")
    const [panels, setPanels] = useState<Panel[]>([])

    const [availableTags, setAvailableTags] = useState<any[]>()
    const [currentTags, setCurrentTags] = useState<SelectOptionObject>()
    const [tagMenuOpen, setTagMenuOpen] = useState(false)

    const [timespan, setTimespan] = useState(31 * 86400)
    const [lineType, setLineType] = useState("linear")

    const doFetchDashboard = (testId: number, tags?: string) => fetchDashboard(testId, tags).then(response => {
        setDashboardUrl(response.url)
        setPanels(response.panels)
    }, error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error)))

    useEffect(() => {
        if (!selectedTest) {
            return;
        }
        if (!tags) {
            history.replace(history.location.pathname + "?test=" + selectedTest)
        }
        fetchTags(selectedTest.id).then((response: any[]) => {
            setAvailableTags(response)
            const paramTags = tags && response.find(t => convertTags(t) === tags)
            setCurrentTags(paramTags && { ...paramTags, toString: () => convertTags(paramTags) } || undefined)
            if (!response || response.length === 0 || response[0] === null) {
                doFetchDashboard(selectedTest.id)
            }
        }, error => dispatch(alertAction("TAGS_FETCH", "Failed to fetch test tags", error)))
    }, [selectedTest])
    useEffect(() => {
        if (currentTags) {
            history.replace(history.location.pathname + "?test=" + selectedTest + "&tags=" + currentTags)
            if (selectedTest) {
                doFetchDashboard(selectedTest.id, currentTags.toString())
            }
        }
    }, [currentTags])
    const [selectedChange, setSelectedChange] = useState<number>()
    const [selectedVariable, setSelectedVariable] = useState<number>()
    return (
        <Card>
            <CardHeader>
                { <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                    <div style={{ display: "flex"}}>
                        <TestSelect
                            placeholderText="Choose test..."
                            initialTestName={test || undefined}
                            onSelect={selection => {
                                setPanels([])
                                setDashboardUrl("")
                                setSelectedTest(selection as SelectedTest)
                                setCurrentTags(undefined)
                                history.replace(history.location.pathname + "?test=" + selection)
                            }}
                            selection={selectedTest}
                        />
                        { selectedTest && availableTags && availableTags.length > 0 && availableTags[0] != null &&
                        <Select
                            isOpen={tagMenuOpen}
                            onToggle={ setTagMenuOpen }
                            selections={currentTags}
                            onSelect={(_, item) => {
                                setCurrentTags(item)
                                setTagMenuOpen(false)
                            }}
                            placeholderText="Choose tags..."
                        >{ availableTags.map((tags: any, i: number) => {
                            return (<SelectOption
                                    key={i}
                                    value={ { ...tags, toString: () => convertTags(tags) } }
                            />) })
                        }</Select>
                        }
                        { selectedTest && <>
                        <NavLink className="pf-c-button pf-m-primary"
                                to={ "/test/" + selectedTest.id + "#vars" }
                        >Variable definitions</NavLink>
                        <Button variant="secondary"
                                onClick={() => window.open(dashboardUrl, "_blank") }
                        >Open Grafana</Button>
                        </>}
                    </div>
                    <div style={{ display: "flex"}}>
                        <TimespanSelect onChange={setTimespan}/>
                        <LineTypeSelect onChange={setLineType}/>
                    </div>
                </div>}
            </CardHeader>
            <CardBody>
                { selectedTest && panels && panels.map((p, i) =>
                    <DataList key={i} aria-label="test variables">
                        <DataListItem aria-labelledby="variable-name">
                            { dashboardUrl &&
                            <DataListItemRow>
                                <DataListItemCells dataListCells={[
                                    <DataListCell key="chart">
                                        <PanelChart
                                            title={ p.name }
                                            variables={ p.variables.map(v => v.id)}
                                            tags={ currentTags?.toString() || "" }
                                            timespan={timespan}
                                            lineType={lineType}
                                            onChangeSelected={(changeId, variableId, runId) => {
                                                setSelectedChange(changeId)
                                                setSelectedVariable(variableId)
                                                // we cannot scroll to an element that's not visible yet
                                                window.setTimeout(() => {
                                                    const element = document.getElementById("change_" + changeId)
                                                    if (element && element !== null) {
                                                        element.scrollIntoView()
                                                    }
                                                    // this is hacky way to reopen tabs on subsequent click
                                                    setSelectedVariable(undefined)
                                                }, 100)
                                            }} />
                                    </DataListCell>,
                                ]} />
                            </DataListItemRow>
                            }
                            <DataListItemRow>
                                <DataListItemCells dataListCells={[
                                    <DataListCell key="changes">
                                        <ChangesTabs
                                            variables={p.variables}
                                            testOwner={selectedTest.owner}
                                            selectedChangeId={selectedChange}
                                            selectedVariableId={selectedVariable}
                                        />
                                    </DataListCell>
                                ]}/>
                            </DataListItemRow>
                        </DataListItem>
                    </DataList>
                )}
                { !selectedTest &&
                    <Bullseye>
                        <EmptyState>
                            <Title headingLevel="h2">No test selected</Title>
                            <EmptyStateBody>Please select one of the tests above</EmptyStateBody>
                        </EmptyState>
                    </Bullseye>
                }
                { selectedTest && panels.length == 0 &&
                    <Bullseye>
                    <EmptyState>
                        <Title headingLevel="h2">Test { selectedTest.toString() } does not define any regression variables</Title>
                        <NavLink
                            className="pf-c-button pf-m-primary"
                            to={ "/test/" + selectedTest.id + "#vars" }
                        >Define regression variables</NavLink>
                    </EmptyState>
                </Bullseye>
                }
            </CardBody>
        </Card>
    )
}