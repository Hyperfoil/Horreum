import React, { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ReferenceDot,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts"
import { Bullseye, Button, EmptyState, Spinner, Title } from "@patternfly/react-core"
import { DateTime } from "luxon"
import {alertingApi, AnnotationDefinition, FingerprintValue, TimeseriesTarget} from "../../api"
import { fingerprintToString } from "../../utils"
import { fetchDatapoints, fetchAllAnnotations } from "./Changes"
import { alertAction, dispatchInfo } from "../../alerts"

function tsToDate(timestamp: number) {
    return DateTime.fromMillis(timestamp).toFormat("yyyy-LL-dd")
}

function formatValue(value: number | string) {
    if (typeof value === "string") {
        value = parseInt(value)
    }
    let suffix = ""
    if (value > 10000000) {
        value /= 1000000
        suffix = " M"
    }
    if (value > 10000) {
        value /= 1000
        suffix = " k"
    }
    return Number(value).toFixed(2) + suffix
}

function ellipsis(str: string) {
    if (!str || str.length < 16) {
        return str
    }
    const firstHalf = str.substring(0, str.length - 16)
    const secondHalf = str.substring(str.length - 16)
    return (
        <div
            style={{
                width: "100%",
                display: "inline-flex",
                flexWrap: "nowrap",
            }}
        >
            <span
                style={{
                    flex: "0 1 content",
                    textOverflow: "ellipsis",
                    overflow: "hidden",
                    whiteSpace: "nowrap",
                }}
            >
                {firstHalf}
            </span>
            <span
                style={{
                    flex: "1 0 content",
                }}
            >
                {secondHalf}
            </span>
        </div>
    )
}

type PanelProps = {
    title: string
    variables: number[]
    fingerprint: FingerprintValue | undefined
    timespan: number
    endTime: number
    setEndTime(endTime: number): void
    lineType: string
    onChangeSelected(changeId: number, variableId: number, runId: number): void
}

const colors = ["#4caf50", "#FF0000", "#CC0066", "#0066FF", "#42a5f5", "#f1c40f"]

export default function PanelChart({
    title,
    variables,
    fingerprint,
    timespan,
    endTime,
    setEndTime,
    lineType,
    onChangeSelected: propOnChangeSelected,
}: PanelProps) {
    const [legend, setLegend] = useState<any[]>() // Payload is not exported
    const [lines, setLines] = useState<any[]>()
    const [datapoints, setDatapoints] = useState<TimeseriesTarget[]>()
    const [annotations, setAnnotations] = useState<AnnotationDefinition[]>()
    const [gettingLast, setGettingLast] = useState(false)
    const startTime = endTime - timespan * 1000
    useEffect(() => {
        setDatapoints(undefined)
        fetchDatapoints(variables, fingerprint, startTime, endTime).then(response => {
            setLegend(
                response.map((tt, i) => ({
                    id: tt.target,
                    type: "line",
                    value: tt.target,
                    color: colors[i % colors.length],
                }))
            )
            setLines(
                response.map((tt, i) => (
                    <Line
                        type={lineType as any} // should be CurveType but can't import that
                        dot={false}
                        key={tt.target}
                        dataKey={tt.target}
                        stroke={colors[i % colors.length]}
                        isAnimationActive={false}
                    />
                ))
            )
            setDatapoints(response)
        })
    }, [startTime, endTime, variables, fingerprint, lineType])
    useEffect(() => {
        fetchAllAnnotations(variables, fingerprint, startTime, endTime).then(setAnnotations)
    }, [startTime, endTime, variables, fingerprint])

    const chartData = useMemo(() => {
        if (!datapoints) {
            return undefined
        }
        const series = new Map()
        datapoints.forEach(tt => {
            tt.datapoints.forEach(([value, timestamp]) => {
                let point = series.get(timestamp)
                if (!point) {
                    point = { timestamp }
                    series.set(timestamp, point)
                }
                point[tt.target] = value
            })
        })
        return [...series.values()].sort((a, b) => a.timestamp - b.timestamp)
    }, [datapoints])
    const onChangeSelected = propOnChangeSelected
    const changes = useMemo(
        () =>
            annotations?.map(a => {
                let value = undefined
                const tt = a.variableId && datapoints?.find(t => t.variableId === a.variableId)
                if (tt) {
                    const dp = tt.datapoints.find(arr => arr[1] === a.time)
                    if (dp) {
                        value = dp[0]
                    }
                }
                if (value) {
                    return (
                        <ReferenceDot
                            key={a.changeId}
                            x={a.time}
                            y={value}
                            r={8}
                            stroke="red"
                            fill="red"
                            fillOpacity={0.3}
                            onClick={() => onChangeSelected(a.changeId || 0, a.variableId || 0, a.runId || 0)}
                        />
                    )
                } else {
                    return <ReferenceLine key={a.changeId} x={a.time} stroke="red" ifOverflow="extendDomain" />
                }
            }) || [],
        [annotations, datapoints, onChangeSelected]
    )

    const dispatch = useDispatch()
    return (
        <>
            <h2 style={{ width: "100%", textAlign: "center" }}>{title}</h2>
            <div style={{ display: "flex", width: "100%" }}>
                <Button
                    variant="control"
                    style={{ height: 372 }}
                    onClick={() => {
                        setEndTime(endTime - timespan * 250)
                    }}
                >
                    &#8810;
                </Button>
                <div style={{ width: "100%", height: 450 }}>
                    {chartData === undefined && (
                        <Bullseye>
                            <Spinner size="xl" />
                        </Bullseye>
                    )}
                    {chartData?.length === 0 && (
                        <EmptyState>
                            <Title headingLevel="h3">No datapoints in this range</Title>
                            <Button
                                isDisabled={gettingLast}
                                onClick={() => {
                                    setGettingLast(true)
                                    alertingApi
                                        .findLastDatapoints({
                                            variables: variables,
                                            fingerprint: fingerprintToString(fingerprint),
                                        })
                                        .then(
                                            response => {
                                                if (Array.isArray(response) && response.length > 0) {
                                                    setEndTime(
                                                        Math.max(...response.map(({ timestamp }) => timestamp)) + 1
                                                    )
                                                } else {
                                                    dispatchInfo(
                                                        dispatch,
                                                        "NO_DATAPOINT",
                                                        "No datapoints have been found",
                                                        "We could not find any datapoints in the history. Try to recalculate them?",
                                                        3000
                                                    )
                                                }
                                            },
                                            error =>
                                                dispatch(
                                                    alertAction(
                                                        "LAST_DATAPOINTS",
                                                        "Failed to fetch last datapoint timestamps.",
                                                        error
                                                    )
                                                )
                                        )
                                        .finally(() => setGettingLast(false))
                                }}
                            >
                                Find most recent datapoints {gettingLast && <Spinner size="sm" />}
                            </Button>
                        </EmptyState>
                    )}
                    {chartData && chartData.length > 0 && (
                        <ResponsiveContainer width="100%" height={450}>
                            <LineChart data={chartData} style={{ userSelect: "none" }}>
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis
                                    allowDataOverflow={true}
                                    type="number"
                                    scale="time"
                                    angle={-30}
                                    textAnchor="end"
                                    height={50}
                                    dataKey="timestamp"
                                    tick={{ fontSize: 12 }}
                                    tickFormatter={tsToDate}
                                    domain={([min, _]) => [timespan > 366 * 86400 ? min : startTime, endTime]}
                                />
                                <YAxis
                                    width={80}
                                    yAxisId={0}
                                    tickFormatter={formatValue}
                                    tick={{ fontSize: 12 }}
                                    domain={["dataMin", "dataMax"]}
                                />
                                <Legend iconType="line" payload={legend} align="left" />
                                <Tooltip
                                    content={({ active, payload, label }) => {
                                        if (!active) {
                                            return null
                                        }
                                        const timestamp = label
                                            ? typeof label === "number"
                                                ? label
                                                : parseInt(label)
                                            : undefined
                                        const date = timestamp
                                            ? DateTime.fromMillis(timestamp).toFormat("yyyy-LL-dd HH:mm:ss")
                                            : ""
                                        const as = annotations?.filter(a => a.time === timestamp) || []
                                        return (
                                            <>
                                                <div
                                                    className="recharts-default-tooltip"
                                                    style={{
                                                        background: "white",
                                                        border: "1px solid black",
                                                        maxWidth: "400px",
                                                        maxHeight: "500px",
                                                        overflowY: "auto",
                                                        direction: "rtl",
                                                        pointerEvents: "auto",
                                                    }}
                                                >
                                                    <div style={{ direction: "ltr", marginLeft: 5 }}>
                                                        {date}
                                                        <table id="toolTip">
                                                            <tbody>
                                                                {payload?.map((row, i) => (
                                                                    <tr key={i}>
                                                                        <td
                                                                            style={{
                                                                                textAlign: "left",
                                                                                color: row.color,
                                                                                paddingRight: "20px",
                                                                                maxWidth: "300px",
                                                                            }}
                                                                        >
                                                                            {ellipsis("" + row.name)}
                                                                        </td>
                                                                        <td>
                                                                            {formatValue(row.value as string | number)}
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                        {as.map(a => (
                                                            <React.Fragment key={a.changeId}>
                                                                <strong>{a.title}</strong>
                                                                <div
                                                                    style={{ fontSize: 12 }}
                                                                    dangerouslySetInnerHTML={{ __html: a.text || "" }}
                                                                />
                                                            </React.Fragment>
                                                        ))}
                                                    </div>
                                                </div>
                                            </>
                                        )
                                    }}
                                />
                                {lines}
                                {changes}
                            </LineChart>
                        </ResponsiveContainer>
                    )}
                </div>
                <Button
                    variant="control"
                    style={{ height: 372 }}
                    onClick={() => {
                        setEndTime(endTime + timespan * 250)
                    }}
                >
                    &#8811;
                </Button>
            </div>
        </>
    )
}
