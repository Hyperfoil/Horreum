import { useState, useEffect, useMemo, useCallback } from "react"
import { useDispatch, useSelector } from "react-redux"
import { ChangesTabs } from "./ChangeTable"
import { alertAction } from "../../alerts"
import TestSelect, { SelectedTest } from "../../components/TestSelect"
import LabelsSelect, { convertLabels } from "../../components/LabelsSelect"
import PanelChart from "./PanelChart"
import { fingerprintToString, formatDate } from "../../utils"
import { teamsSelector } from "../../auth"
import { DateTime } from "luxon"
import {
    PanelInfo,
    AnnotationDefinition,
    TimeseriesTarget,
    alertingApi,
    testApi,
    changesApi,
    FingerprintValue,
} from "../../api"

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
    PageSection,
    Select,
    SelectOption,
    SelectOptionObject,
    Spinner,
    Title,
} from "@patternfly/react-core"
import { NavLink, useHistory } from "react-router-dom"

type TimespanSelectProps = {
    onChange(span: number): void
}

type Timespan = SelectOptionObject & {
    seconds: number
}

function makeTimespan(title: string, seconds: number): Timespan {
    return { seconds, toString: () => title }
}

const TimespanSelect = (props: TimespanSelectProps) => {
    const [isExpanded, setExpanded] = useState(false)
    const options = useMemo(
        () => [
            makeTimespan("all", 2000000000),
            makeTimespan("1 year", 366 * 86400),
            makeTimespan("6 months", 183 * 86400),
            makeTimespan("3 months", 92 * 86400),
            makeTimespan("1 month", 31 * 86400),
            makeTimespan("1 week", 7 * 86400),
            makeTimespan("1 day", 86400),
            makeTimespan("6 hours", 6 * 3600),
        ],
        []
    )
    const [selected, setSelected] = useState(options[4])
    return (
        <Select
            isOpen={isExpanded}
            selections={selected}
            onToggle={setExpanded}
            onSelect={(_, value) => {
                const timespan = value as Timespan
                setSelected(timespan)
                setExpanded(false)
                props.onChange(timespan.seconds)
            }}
        >
            {options.map((timespan, i) => (
                <SelectOption key={i} value={timespan}>
                    {timespan.toString()}
                </SelectOption>
            ))}
        </Select>
    )
}

type LineTypeSelectProps = {
    onChange(type: string): void
}

type LineType = SelectOptionObject & {
    type: string
}

function makeLineType(title: string, type: string): LineType {
    return { type, toString: () => title }
}

const LineTypeSelect = (props: LineTypeSelectProps) => {
    const [isExpanded, setExpanded] = useState(false)
    const options = useMemo(
        () => [
            makeLineType("steps", "stepAfter"),
            makeLineType("straight", "linear"),
            makeLineType("curve", "monotone"),
        ],
        []
    )
    const [selected, setSelected] = useState(options[1])
    return (
        <Select
            isOpen={isExpanded}
            selections={selected}
            onToggle={setExpanded}
            onSelect={(_, value) => {
                const linetype = value as LineType
                setSelected(linetype)
                setExpanded(false)
                props.onChange(linetype.type)
            }}
        >
            {options.map((timespan, i) => (
                <SelectOption key={i} value={timespan}>
                    {timespan.toString()}
                </SelectOption>
            ))}
        </Select>
    )
}

const MONTH = 31 * 86400

function toNumber(value: any) {
    const n = parseInt(value)
    return isNaN(n) ? undefined : n
}

function range(from: number, to: number) {
    return {
        from: new Date(DateTime.fromMillis(from).setZone("utc").toISO()),
        to: new Date(DateTime.fromMillis(to).setZone("utc").toISO()),
        oneBeforeAndAfter: true,
    }
}

export const fetchDatapoints = (
    variableIds: number[],
    fingerprint: FingerprintValue | undefined,
    from: number,
    to: number
): Promise<TimeseriesTarget[]> => {
    const query = {
        range: range(from, to),
        targets: variableIds.map(id => ({
            target: `${id};${fingerprintToString(fingerprint)}`,
            type: "timeseries",
            refId: "ignored",
        })),
    }
    return changesApi.query(query)
}

export const fetchAnnotations = (
    variableId: number,
    fingerprint: FingerprintValue | undefined,
    from: number,
    to: number
): Promise<AnnotationDefinition[]> => {
    const query = {
        range: range(from, to),
        annotation: {
            query: variableId + ";" + fingerprintToString(fingerprint),
        },
    }
    return changesApi.annotations(query)
}

export const fetchAllAnnotations = (
    variableIds: number[],
    fingerprint: FingerprintValue | undefined,
    from: number,
    to: number
): Promise<AnnotationDefinition[]> => {
    // TODO: let's create a bulk operation for these
    return Promise.all(variableIds.map(id => fetchAnnotations(id, fingerprint, from, to))).then(results =>
        results.flat()
    )
}

export const flattenNode = (arr : Array<any> | undefined) => {
    const nodeObj : any = {};
    arr?.forEach(node => {
        nodeObj[`${node.name}`] = ((node.children == null ) ? `${node.value}` : flattenNode(node.children) )
    });
    return nodeObj;
}


export default function Changes() {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    // eslint-disable-next-line
    const paramTest = useMemo(() => params.get("test") || undefined, [])
    const paramFingerprint = params.get("fingerprint")
    const dispatch = useDispatch()
    const teams = useSelector(teamsSelector)
    const [selectedTest, setSelectedTest] = useState<SelectedTest>()
    const [selectedFingerprint, setSelectedFingerprint] = useState<FingerprintValue | undefined>(() => {
        if (!paramFingerprint) {
            return undefined
        }
        try {
            const fingerprint = JSON.parse(paramFingerprint)
            const str = convertLabels(fingerprint)
            return { ...fingerprint, toString: () => str }
        } catch (e) {
            dispatch(
                alertAction(
                    "PARSE_FINGERPRINT",
                    "Fingerprint parsing failed",
                    "Failed to parse fingerprint <code>" + paramFingerprint + "</code>"
                )
            )
            return undefined
        }
    })
    const [panels, setPanels] = useState<PanelInfo[]>([])
    const [loadingPanels, setLoadingPanels] = useState(false)
    const [loadingFingerprints, setLoadingFingerprints] = useState(false)
    const [requiresFingerprint, setRequiresFingerprint] = useState(false)

    const firstNow = useMemo(() => Date.now(), [])
    const [endTime, setEndTime] = useState(toNumber(params.get("end")) || firstNow)
    const [date, setDate] = useState(formatDate(firstNow))
    const [timespan, setTimespan] = useState<number>(toNumber(params.get("timespan")) || MONTH)
    const [lineType, setLineType] = useState(params.get("line") || "linear")

    const createQuery = (alwaysEndTime: boolean) => {
        let query = "?test=" + selectedTest
        if (selectedFingerprint) {
            query += "&fingerprint=" + encodeURIComponent(JSON.stringify(selectedFingerprint))
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
            document.title = "Changes | Horreum"
            return
        }
        document.title = `${selectedTest} | Horreum`
        history.replace(history.location.pathname + createQuery(false))
    }, [selectedTest, selectedFingerprint, endTime, timespan, lineType, firstNow, history])
    useEffect(() => {
        setPanels([])
        // We need to prevent fetching dashboard until we are sure if we need the fingerprint
        if (selectedTest && !loadingFingerprints) {
            setLoadingPanels(true)
            alertingApi
                .dashboard(selectedTest.id, fingerprintToString(selectedFingerprint))
                .then(
                    response => {
                        setPanels(response.panels)
                    },
                    error => dispatch(alertAction("DASHBOARD_FETCH", "Failed to fetch dashboard", error))
                )
                .finally(() => setLoadingPanels(false))
        }
    }, [selectedTest, selectedFingerprint, teams, dispatch])
    useEffect(() => {
        const newDate = formatDate(endTime)
        if (newDate !== date) {
            setDate(newDate)
        }
    }, [endTime /* date omitted intentionally */])
    const [selectedChange, setSelectedChange] = useState<number>()
    const [selectedVariable, setSelectedVariable] = useState<number>()

    const onSelectTest = useCallback((selection, _, isInitial) => {
        if (selection === undefined) {
            setSelectedTest(undefined)
        } else if (selectedTest !== selection) {
            setSelectedTest(selection as SelectedTest)
        }
        if (!isInitial) {
            setSelectedFingerprint(undefined)
        }
    }, [])

    const [linkCopyOpen, setLinkCopyOpen] = useState(false)
    const fingerprintSource = useCallback(() => {
        if (!selectedTest) {
            return Promise.resolve([])
        }
        setLoadingFingerprints(true)
        return testApi
            .listFingerprints(selectedTest.id)
            .then(
                response => {
                    setRequiresFingerprint(!!response && response.length > 1)
                    const flattenedFingerprints : any[] = [];
                    response.forEach(fingerprint => {
                        flattenedFingerprints.push(flattenNode(fingerprint.values));
                    })
                    return flattenedFingerprints
                },
                error => {
                    dispatch(alertAction("FINGERPRINT_FETCH", "Failed to fetch test fingerprints", error))
                    return error
                }
            )
            .finally(() => {
                setLoadingFingerprints(false)
            })
    }, [selectedTest])
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    {
                        <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                            <div style={{ display: "flex", flexWrap: "wrap", width: "100%" }}>
                                <TestSelect
                                    style={{ width: "fit-content" }}
                                    initialTestName={paramTest}
                                    onSelect={onSelectTest}
                                    selection={selectedTest}
                                />
                                {selectedTest && (
                                    <LabelsSelect
                                        style={{ width: "fit-content" }}
                                        selection={selectedFingerprint}
                                        onSelect={setSelectedFingerprint}
                                        source={fingerprintSource}
                                        forceSplit={true}
                                    />
                                )}
                                {selectedTest && (
                                    <>
                                        <NavLink
                                            className="pf-c-button pf-m-primary"
                                            to={"/test/" + selectedTest.id + "#vars"}
                                        >
                                            Variable definitions
                                        </NavLink>
                                        <Button
                                            variant="secondary"
                                            isDisabled={
                                                !selectedTest ||
                                                loadingFingerprints ||
                                                (requiresFingerprint && !selectedFingerprint)
                                            }
                                            onClick={() => setLinkCopyOpen(true)}
                                        >
                                            Copy link
                                        </Button>
                                        <Modal
                                            variant="small"
                                            title="Copy link to this chart"
                                            isOpen={linkCopyOpen}
                                            onClose={() => setLinkCopyOpen(false)}
                                            actions={[
                                                <Button key="cancel" onClick={() => setLinkCopyOpen(false)}>
                                                    Close
                                                </Button>,
                                            ]}
                                        >
                                            <ClipboardCopy
                                                isReadOnly={true}
                                                onCopy={() => setTimeout(() => setLinkCopyOpen(false), 1000)}
                                            >
                                                {window.location.origin + window.location.pathname + createQuery(true)}
                                            </ClipboardCopy>
                                        </Modal>
                                    </>
                                )}
                            </div>
                            <div style={{ display: "flex" }}>
                                <DatePicker
                                    value={date}
                                    onChange={(event, value) => {
                                        setDate(value)
                                        const dateTime = DateTime.fromFormat(value, "yyyy-MM-dd")
                                        if (dateTime.isValid) {
                                            setEndTime(dateTime.toMillis())
                                        }
                                    }}
                                />
                                <TimespanSelect onChange={setTimespan} />
                                <LineTypeSelect onChange={setLineType} />
                            </div>
                        </div>
                    }
                </CardHeader>
                <CardBody>
                    {!selectedTest && (
                        <EmptyState>
                            <Title headingLevel="h2">No test selected</Title>
                            <EmptyStateBody>Please select one of the tests above</EmptyStateBody>
                        </EmptyState>
                    )}
                    {selectedTest && loadingFingerprints && (
                        <EmptyState>
                            <EmptyStateBody>
                                Loading fingerprints... <Spinner size="md" />
                            </EmptyStateBody>
                        </EmptyState>
                    )}
                    {selectedTest && !loadingFingerprints && requiresFingerprint && !selectedFingerprint && (
                        <EmptyState>
                            <Title headingLevel="h2">Please select datasets fingerprint.</Title>
                        </EmptyState>
                    )}
                    {selectedTest && !loadingPanels && !requiresFingerprint && panels.length === 0 && (
                        <EmptyState>
                            <Title headingLevel="h2">
                                Test {selectedTest.toString()} does not define any change detection variables
                            </Title>
                            <NavLink className="pf-c-button pf-m-primary" to={"/test/" + selectedTest.id + "#vars"}>
                                Define change detection variables
                            </NavLink>
                        </EmptyState>
                    )}
                    {!loadingFingerprints &&
                        (!requiresFingerprint || selectedFingerprint) &&
                        panels &&
                        panels.map((p, i) => (
                            <DataList key={i} aria-label="test variables">
                                <DataListItem aria-labelledby="variable-name">
                                    <DataListItemRow>
                                        <DataListItemCells
                                            dataListCells={[
                                                <DataListCell key="chart">
                                                    <PanelChart
                                                        title={p.name}
                                                        variables={p.variables.map(v => v.id)}
                                                        fingerprint={selectedFingerprint}
                                                        endTime={endTime}
                                                        setEndTime={setEndTime}
                                                        timespan={timespan}
                                                        lineType={lineType}
                                                        onChangeSelected={(changeId, variableId) => {
                                                            setSelectedChange(changeId)
                                                            setSelectedVariable(variableId)
                                                            // we cannot scroll to an element that's not visible yet
                                                            window.setTimeout(() => {
                                                                const element = document.getElementById(
                                                                    "change_" + changeId
                                                                )
                                                                if (element && element !== null) {
                                                                    element.scrollIntoView()
                                                                }
                                                                // this is hacky way to reopen tabs on subsequent click
                                                                setSelectedVariable(undefined)
                                                            }, 100)
                                                        }}
                                                    />
                                                </DataListCell>,
                                            ]}
                                        />
                                    </DataListItemRow>
                                    <DataListItemRow>
                                        <DataListItemCells
                                            dataListCells={[
                                                <DataListCell key="changes">
                                                    <ChangesTabs
                                                        variables={p.variables}
                                                        fingerprint={selectedFingerprint}
                                                        testOwner={selectedTest?.owner}
                                                        selectedChangeId={selectedChange}
                                                        selectedVariableId={selectedVariable}
                                                    />
                                                </DataListCell>,
                                            ]}
                                        />
                                    </DataListItemRow>
                                </DataListItem>
                            </DataList>
                        ))}
                </CardBody>
            </Card>
        </PageSection>
    )
}
