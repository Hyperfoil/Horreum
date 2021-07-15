
import React, { useEffect, useMemo, useState } from 'react'
import { AutoSizer } from 'react-virtualized';
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ReferenceDot,
    ReferenceLine,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';
import {
    Button,
    EmptyState,
    Title,
} from '@patternfly/react-core'
import { DateTime } from 'luxon';
import { Annotation, fetchDatapoints, fetchAllAnnotations, TimeseriesTarget } from './grafanaapi'

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
    return Number(value).toFixed(2) + suffix;
}

type PanelProps = {
    title: string,
    variables: number[],
    tags: string,
    timespan: number,
    lineType: string,
    onChangeSelected(changeId: number, variableId: number, runId: number): void,
}

const colors = [ "#4caf50", "#FF0000", "#CC0066", "#0066FF", "#42a5f5", "#f1c40f"]

export default function PanelChart(props: PanelProps) {
    const now = useMemo(() => Date.now(), [])
    const [domain, setDomain] = useState<[number, number]>([now - props.timespan * 1000, now])
    const [legend, setLegend] = useState<any[]>() // Payload is not exported
    const [lines, setLines] = useState<any[]>()
    const [datapoints, setDatapoints] = useState<TimeseriesTarget[]>()
    const [annotations, setAnnotations] = useState<Annotation[]>()
    useEffect(() => {
        setDomain([now - props.timespan * 1000, now])
    }, [props.timespan, now])
    useEffect(() => {
        fetchDatapoints(props.variables, props.tags, domain[0], domain[1]).then(response => {
            setLegend(response.map((tt, i) => ({
                id: tt.target,
                type: "line",
                value: tt.target,
                color: colors[i % colors.length]
            })))
            setLines(response.map((tt, i) => (
                <Line
                    type={props.lineType as any} // should be CurveType but can't import that
                    dot={false}
                    key={tt.target}
                    dataKey={tt.target}
                    stroke={ colors[i % colors.length]}
                    isAnimationActive={false}
                />)))
            setDatapoints(response)
        })
    }, [domain, props.variables, props.tags, props.lineType])
    useEffect(() => {
        fetchAllAnnotations(props.variables, props.tags, domain[0] as number, domain[1] as number).then(setAnnotations)
    }, [domain, props.variables, props.tags])

    const chartData = useMemo(() => {
        if (!datapoints) {
            return []
        }
        let series = new Map()
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
    const onChangeSelected = props.onChangeSelected
    const changes = useMemo(() => annotations?.map(a => {
        let value = undefined
        const tt = a.variableId && datapoints?.find(t => t.variableId === a.variableId)
        if (tt) {
            const dp = tt.datapoints.find(arr => arr[1] === a.time)
            if (dp) {
                value = dp[0]
            }
        }
        if (value) {
            return (<ReferenceDot
                key={a.changeId}
                x={a.time}
                y={value}
                r={8}
                stroke="red"
                fill="red"
                fillOpacity={0.3}
                onClick={ () => onChangeSelected(a.changeId || 0, a.variableId || 0, a.runId || 0)}
            />)
        } else {
            return (<ReferenceLine
                key={a.changeId}
                x={a.time}
                stroke="red"
                ifOverflow="extendDomain"
            />)
        }
    }) || [], [annotations, datapoints, onChangeSelected]);
    return (<>
        <h2 style={{ width: "100%", textAlign: "center"}}>{ props.title }</h2>
        <div style={{ display: "flex", width: "100%"}}>
            <Button
                variant="control"
                style={{height: 372}}
                onClick={() => {
                    const domainSpan = domain[1] - domain[0]
                    setDomain([domain[0] - domainSpan / 4, domain[1] - domainSpan / 4])
                }}
            >&#8810;</Button>
            <div style={{ width: "100%", height: 450 }}>
                { chartData.length === 0 && <EmptyState><Title headingLevel="h3">No datapoints in this range</Title></EmptyState>}
                { chartData.length > 0 &&
                <AutoSizer disableHeight={true}>{({ height, width }) => (
                    <LineChart
                        width={width}
                        height={ 450 }
                        data={chartData}
                        style={{ userSelect: 'none' }}
                    >
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
                            domain={domain}
                        />
                        <YAxis
                            width={80}
                            yAxisId={0}
                            tickFormatter={formatValue}
                            tick={{ fontSize: 12 }}
                            domain={['dataMin', 'dataMax']}
                        />
                        <Legend iconType="line" payload={legend} align="left" />
                        <Tooltip content={({active, payload, label}) => {
                            if (!active) {
                                return null
                            }
                            const timestamp = label ? typeof label === "number" ? label : parseInt(label) : undefined
                            const date = timestamp ? DateTime.fromMillis(timestamp).toFormat("yyyy-LL-dd HH:mm:ss") : "";
                            const as = annotations?.filter(a => a.time === timestamp) || []
                            return (<>
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
                                ><div style={{ direction: "ltr", marginLeft: 5}}>
                                    {date}
                                    <table id="toolTip">
                                        <tbody>
                                            { payload?.map((row, i) => (<tr key={i}>
                                                <td style={{ textAlign: "left", color: row.color, paddingRight: "20px" }}>{row.name}</td>
                                                <td>{formatValue(row.value as string | number)}</td>
                                            </tr>)) }
                                        </tbody>
                                </table>
                                { as.map(a => (<React.Fragment key={a.changeId}>
                                    <strong>{ a.title }</strong>
                                    <div style={{ fontSize: 12 }} dangerouslySetInnerHTML={{ __html: a.text }} />
                                </React.Fragment >))}
                                </div></div>
                            </>)
                        }} />
                    {lines}
                    {changes}
                    </LineChart>
                    )}</AutoSizer>
                }
                </div>
            <Button
                variant="control"
                style={{height: 372}}
                onClick={() => {
                    const domainSpan = domain[1] - domain[0]
                    setDomain([domain[0] + domainSpan / 4, domain[1] + domainSpan / 4])
                }}
            >&#8811;</Button>
        </div>
    </>)
}