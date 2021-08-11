import { useState, useEffect, useMemo, useCallback } from 'react'
import {  useDispatch, useSelector } from 'react-redux'
import { fetchDashboard } from './api'
import { Panel } from './types'
import { ChangesTabs } from './Changes'
import { alertAction } from '../../alerts'
import TestSelect, { SelectedTest } from '../../components/TestSelect'
import TagsSelect, { SelectedTags } from '../../components/TagsSelect'
import PanelChart from './PanelChart'
import { formatDate } from '../../utils'
import { DateTime } from 'luxon'
import { rolesSelector } from '../../auth'

import {
    Button,
    Card,
    CardBody,
    CardHeader,
    ClipboardCopy,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    DatePicker,
    EmptyState,
    EmptyStateBody,
    Modal,
    Select,
    SelectOption,
    SelectOptionObject,
    Spinner,
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

const MONTH = 31 * 86400

function toNumber(value: any) {
    const n = parseInt(value)
    return isNaN(n) ? undefined : n;
}

export default function Series() {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    // eslint-disable-next-line
    const paramTest = useMemo(() => params.get("test") || undefined, [])
    const paramTags = params.get("tags")
    const dispatch = useDispatch()
    const roles = useSelector(rolesSelector)
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [currentTags, setCurrentTags] = useState<SelectedTags | undefined>(paramTags ? ({ toString: () => paramTags }) : undefined)
    const [dashboardUrl, setDashboardUrl] = useState("")
    const [panels, setPanels] = useState<Panel[]>([])
    const [loadingPanels, setLoadingPanels] = useState(false)
    const [loadingTags, setLoadingTags] = useState(false)
    const [requiresTags, setRequiresTags] = useState(false)

    const firstNow = useMemo(() => Date.now(), [])
    const [endTime, setEndTime] = useState(toNumber(params.get("end")) || firstNow)
    const [date, setDate] = useState(formatDate(firstNow))
    const [timespan, setTimespan] = useState<number>(toNumber(params.get("timespan")) || MONTH)
    const [lineType, setLineType] = useState(params.get("line") || "linear")

    const createQuery = (alwaysEndTime: boolean) => {
        let query = "?test=" + selectedTest
        if (currentTags) {
            query += "&tags=" + currentTags
        }
        if (endTime !== firstNow || alwaysEndTime) {
            query += "&end=" + endTime
        }
        if (timespan !== MONTH) {
            query += "&timespan=" + timespan
        }
        if (lineType !== "linear") {
            query += "&line=" + lineType
        }
        return query
    }
    useEffect(() => {
        if (!selectedTest) {
            return
        }
        history.replace(history.location.pathname + createQuery(false))
    }, [selectedTest, currentTags, endTime, timespan, lineType, firstNow, history])
    useEffect(() => {
        setPanels([])
        setDashboardUrl("")
        // We need to prevent fetching dashboard until we are sure if we need the tags
        if (selectedTest && !loadingTags) {
            setLoadingPanels(true)
            fetchDashboard(selectedTest.id, currentTags?.toString()).then(response => {
                setDashboardUrl(response.url)
                setPanels(response.panels)
            }, error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error)))
            .finally(() => setLoadingPanels(false))
        }
    }, [selectedTest, currentTags, roles, dispatch])
    useEffect(() => {
        const newDate = formatDate(endTime)
        if (newDate !== date) {
            setDate(newDate)
        }
    }, [ endTime, /* date omitted intentionally */ ])
    const [selectedChange, setSelectedChange] = useState<number>()
    const [selectedVariable, setSelectedVariable] = useState<number>()

    const onSelectTest = useCallback((selection, isInitial) => {
        if (selectedTest !== selection) {
            setSelectedTest(selection as SelectedTest)
        }
        if (!isInitial) {
            setCurrentTags(undefined)
        }
    }, [])
    const beforeTagsLoading = useCallback(() => setLoadingTags(true), [])
    const onTagsLoaded = useCallback(tags => {
        setLoadingTags(false)
        setRequiresTags(!!tags && tags.length > 1)
    }, [])
    const [linkCopyOpen, setLinkCopyOpen] = useState(false)
    return (
        <Card>
            <CardHeader>
                { <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                    <div style={{ display: "flex"}}>
                        <TestSelect
                            placeholderText="Choose test..."
                            initialTestName={paramTest}
                            onSelect={ onSelectTest }
                            selection={ selectedTest }
                        />
                        { selectedTest &&
                        <TagsSelect
                            testId={ selectedTest?.id }
                            selection={ currentTags }
                            onSelect={ setCurrentTags }
                            showIfNoTags={false}
                            beforeTagsLoading={ beforeTagsLoading }
                            onTagsLoaded={ onTagsLoaded  }
                        /> }
                        { selectedTest && <>
                        <NavLink className="pf-c-button pf-m-primary"
                                to={ "/test/" + selectedTest.id + "#vars" }
                        >Variable definitions</NavLink>
                        <Button variant="secondary"
                                onClick={() => window.open(dashboardUrl, "_blank") }
                        >Open Grafana</Button>
                        <Button variant="secondary"
                                isDisabled={ !selectedTest || loadingTags || (requiresTags && !currentTags)}
                                onClick={() => setLinkCopyOpen(true) }
                        >Copy link</Button>
                        <Modal
                            variant="small"
                            title="Copy link to this chart"
                            isOpen={ linkCopyOpen }
                            onClose={ () => setLinkCopyOpen(false) }
                            actions={[
                                <Button
                                    key="cancel"
                                    onClick={() => setLinkCopyOpen(false)}
                                >Close</Button>
                            ]}
                        >
                            <ClipboardCopy
                                isReadOnly={ true }
                                onCopy={ () => setTimeout(() => setLinkCopyOpen(false), 1000)}>
                                { window.location.origin + window.location.pathname + createQuery(true) }
                            </ClipboardCopy>
                        </Modal>
                        </>}
                    </div>
                    <div style={{ display: "flex"}}>
                        <DatePicker
                            value={ date }
                            onChange={ value => {
                                setDate(value)
                                const dateTime = DateTime.fromFormat(value, 'yyyy-MM-dd')
                                if (dateTime.isValid) {
                                    setEndTime(dateTime.toMillis())
                                }
                            }}
                        />
                        <TimespanSelect onChange={setTimespan}/>
                        <LineTypeSelect onChange={setLineType}/>
                    </div>
                </div>}
            </CardHeader>
            <CardBody>
                { !selectedTest &&
                    <EmptyState>
                        <Title headingLevel="h2">No test selected</Title>
                        <EmptyStateBody>Please select one of the tests above</EmptyStateBody>
                    </EmptyState>
                }
                { selectedTest && loadingTags &&
                    <EmptyState>
                        <EmptyStateBody>Loading tags... <Spinner size="md"/></EmptyStateBody>
                    </EmptyState>
                }
                { selectedTest && !loadingTags && requiresTags && !currentTags &&
                    <EmptyState>
                        <Title headingLevel="h2">Please select tags filtering test runs.</Title>
                    </EmptyState>
                }
                { selectedTest && !loadingPanels && !requiresTags && panels.length === 0 &&
                    <EmptyState>
                        <Title headingLevel="h2">Test { selectedTest.toString() } does not define any regression variables</Title>
                        <NavLink
                            className="pf-c-button pf-m-primary"
                            to={ "/test/" + selectedTest.id + "#vars" }
                        >Define regression variables</NavLink>
                    </EmptyState>
                }
                { !loadingTags && (!requiresTags || currentTags) && panels && panels.map((p, i) =>
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
                                            endTime={ endTime }
                                            setEndTime={ setEndTime }
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
                                            testOwner={selectedTest?.owner}
                                            selectedChangeId={selectedChange}
                                            selectedVariableId={selectedVariable}
                                        />
                                    </DataListCell>
                                ]}/>
                            </DataListItemRow>
                        </DataListItem>
                    </DataList>
                )}
            </CardBody>
        </Card>
    )
}