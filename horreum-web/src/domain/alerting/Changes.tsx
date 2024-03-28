import {useState, useEffect, useMemo, useCallback, useContext} from "react"
import { ChangesTabs } from "./ChangeTable"
import { SelectedTest } from "../../components/TestSelect"
import LabelsSelect, { convertLabels, SelectedLabels } from "../../components/LabelsSelect"
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
	DataList,
	DataListItem,
	DataListItemRow,
	DataListItemCells,
	DataListCell,
	DatePicker,
	EmptyState,
	EmptyStateBody,
	Spinner, EmptyStateHeader, EmptyStateFooter,
} from '@patternfly/react-core';
import {
	Select,
	SelectOption,
	SelectOptionObject
} from '@patternfly/react-core/deprecated';
import { useNavigate } from "react-router-dom"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {useSelector} from "react-redux";

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
            onToggle={(_event, val) => setExpanded(val)}
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
            onToggle={(_event, val) => setExpanded(val)}
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
        nodeObj[node.name] = ((node.children == null ) ? node.value : flattenNode(node.children) )
    });
    return nodeObj;
}

type ChangesProps = {
    testID: number
}


export default function Changes(props: ChangesProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const navigate = useNavigate()
    const params = new URLSearchParams(location.search)
    // eslint-disable-next-line
    const paramTest = useMemo(() => params.get("test") || undefined, [])
    const paramFingerprint = params.get("fingerprint")
    const teams = useSelector(teamsSelector)
    const newTest = {} as SelectedTest;
    newTest.id = props.testID
    const [selectedTest, setSelectedTest] = useState<SelectedTest>(newTest)
    const [selectedFingerprint, setSelectedFingerprint] = useState<FingerprintValue | undefined>(() => {
        if (!paramFingerprint) {
            return undefined
        }
        try {
            const fingerprint = JSON.parse(paramFingerprint)
            const str = convertLabels(fingerprint)
            return { ...fingerprint, toString: () => str }
        } catch (e) {
            alerting.dispatchError("Failed to parse fingerprint <code>" + paramFingerprint + "</code>",
                "PARSE_FINGERPRINT",
                "Fingerprint parsing failed")
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
        navigate(location.pathname + createQuery(false))
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
                    error => alerting.dispatchError(error, "DASHBOARD_FETCH", "Failed to fetch dashboard")
                )
                .finally(() => setLoadingPanels(false))
        }
    }, [selectedTest, selectedFingerprint, teams])
    useEffect(() => {
        const newDate = formatDate(endTime)
        if (newDate !== date) {
            setDate(newDate)
        }
    }, [endTime /* date omitted intentionally */])
    const [selectedChange, setSelectedChange] = useState<number>()
    const [selectedVariable, setSelectedVariable] = useState<number>()

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
                    alerting.dispatchError(error, "FINGERPRINT_FETCH", "Failed to fetch test fingerprints")
                    return error
                }
            )
            .finally(() => {
                setLoadingFingerprints(false)
            })
    }, [selectedTest])
    return (
            <Card>
                <CardHeader>
                    {
                        <div style={{ display: "flex", justifyContent: "space-between", width: "100%" }}>
                            <div style={{ display: "flex", flexWrap: "wrap", width: "100%" }}>
                                {selectedTest && (
                                    <LabelsSelect
                                        style={{ width: "fit-content" }}
                                        selection={selectedFingerprint}
                                        onSelect={setSelectedFingerprint}
                                        source={fingerprintSource}
                                        forceSplit={true}
                                    />
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
                            <EmptyStateHeader titleText="No test selected" headingLevel="h2" />
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
                            <EmptyStateHeader titleText="Please select datasets fingerprint." headingLevel="h2" />
                        </EmptyState>
                    )}
                    {selectedTest && !loadingPanels && !requiresFingerprint && panels.length === 0 && (
                        <EmptyState>
                            <EmptyStateHeader titleText={<>No change detection variables defined</>} headingLevel="h2" /><EmptyStateFooter>
                        </EmptyStateFooter></EmptyState>
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
    )
}
