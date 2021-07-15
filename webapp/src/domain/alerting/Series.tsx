import React, { useState, useEffect, useMemo, useCallback } from 'react'
import {  useDispatch } from 'react-redux'
import { fetchDashboard } from './api'
import { Panel } from './types'
import { ChangesTabs } from './Changes'
import { alertAction } from '../../alerts'
import TestSelect, { SelectedTest } from '../../components/TestSelect'
import TagsSelect from '../../components/TagsSelect'
import PanelChart from './PanelChart'

import {
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

export default function Series() {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    const test = params.get("test")
    const tags = params.get("tags")
    const dispatch = useDispatch()
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [currentTags, setCurrentTags] = useState<SelectOptionObject>()
    const [dashboardUrl, setDashboardUrl] = useState("")
    const [panels, setPanels] = useState<Panel[]>([])
    const [requiresTags, setRequiresTags] = useState(false)

    const [timespan, setTimespan] = useState(31 * 86400)
    const [lineType, setLineType] = useState("linear")

    const doFetchDashboard = useCallback((testId: number, tags?: string) =>
        fetchDashboard(testId, tags).then(response => {
            setDashboardUrl(response.url)
            setPanels(response.panels)
        }, error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error)))
    , [dispatch])
    useEffect(() => {
        if (selectedTest) {
            doFetchDashboard(selectedTest.id)
        }
    }, [selectedTest, doFetchDashboard])

    useEffect(() => {
        if (!selectedTest) {
            return;
        }
        if (!tags) {
            history.replace(history.location.pathname + "?test=" + selectedTest)
        }
    }, [selectedTest, history, tags])
    useEffect(() => {
        if (currentTags) {
            history.replace(history.location.pathname + "?test=" + selectedTest + "&tags=" + currentTags)
            if (selectedTest) {
                doFetchDashboard(selectedTest.id, currentTags.toString())
            }
        }
    }, [currentTags, doFetchDashboard, history, selectedTest])
    const [selectedChange, setSelectedChange] = useState<number>()
    const [selectedVariable, setSelectedVariable] = useState<number>()
    const onTagsLoaded = useCallback(tags => setRequiresTags(!!tags && tags.length > 1), [])
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
                        { selectedTest &&
                        <TagsSelect
                            testId={ selectedTest?.id }
                            initialTags={ tags || undefined }
                            selection={ currentTags }
                            onSelect={ setCurrentTags }
                            tagFilter={ t => !!t }
                            showIfNoTags={false}
                            onTagsLoaded={ onTagsLoaded  }
                        /> }
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
                { selectedTest && requiresTags && !currentTags &&
                    <EmptyState>
                        <EmptyStateBody>Please select tags filtering test runs.</EmptyStateBody>
                    </EmptyState>
                }
                { selectedTest && (!requiresTags || currentTags) && panels && panels.map((p, i) =>
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
                    <EmptyState>
                        <Title headingLevel="h2">No test selected</Title>
                        <EmptyStateBody>Please select one of the tests above</EmptyStateBody>
                    </EmptyState>
                }
                { selectedTest && panels.length === 0 &&
                    <EmptyState>
                        <Title headingLevel="h2">Test { selectedTest.toString() } does not define any regression variables</Title>
                        <NavLink
                            className="pf-c-button pf-m-primary"
                            to={ "/test/" + selectedTest.id + "#vars" }
                        >Define regression variables</NavLink>
                    </EmptyState>
                }
            </CardBody>
        </Card>
    )
}