import React, { useEffect, useState } from "react"
import { useHistory } from "react-router-dom"

import {
    ActionGroup,
    Button,
    Checkbox,
    Level,
    LevelItem,
    Popover,
    Switch,
    TextArea,
    Title,
    Tooltip,
} from "@patternfly/react-core"
import { TableComposable, Thead, Tbody, Tr, Th, Td } from "@patternfly/react-table"
import { EditIcon, HelpIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, XAxis, YAxis } from "recharts"
import ReactMarkdown from "react-markdown"

import Api, { TableReportData, TableReport, TableReportConfig, ReportComment } from "../../api"
import { formatDateTime } from "../../utils"
import { colors } from "../../charts"
import "./TableReportView.css"
import "github-markdown-css"

function formatter(func: string | undefined) {
    // eslint-disable-next-line
    return func ? new Function("return " + func)() : (x: string) => x
}

type DataViewProps = {
    config: TableReportConfig
    data?: TableReportData
    baseline?: TableReportData
    unit?: string
    selector(d: TableReportData): number
    siblingSelector(d: TableReportData, index: number): number
}

function DataView(props: DataViewProps) {
    if (props.data === undefined) {
        return <>(no data)</>
    }
    const data = props.data
    const value = data && props.selector(data)

    let change = undefined
    if (props.baseline !== undefined && props.baseline !== data) {
        const baseline = props.selector(props.baseline)
        if (!isNaN(value) && !isNaN(baseline) && baseline !== 0) {
            change = (value / baseline - 1) * 100
        }
    }

    return (
        <Popover
            headerContent={
                <>
                    Dataset{" "}
                    <NavLink to={`/run/${data.runId}#dataset${data.ordinal}`}>
                        {data.runId}/{data.ordinal + 1}
                    </NavLink>
                </>
            }
            bodyContent={
                <TableComposable variant="compact">
                    <Tbody>
                        {props.config.components.map((c, i) => (
                            <Tr key={i}>
                                <Th>{c.name}</Th>
                                <Td>{props.siblingSelector(data, i)}</Td>
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            }
        >
            <Button variant="link">
                {value}
                {props.unit}
                {change !== undefined && ` (${change > 0 ? "+" : ""}${change.toFixed(2)}%)`}
            </Button>
        </Popover>
    )
}

type ComponentTableProps = {
    baseline?: string
    onBaselineChange(scale: string | undefined): void
    config: TableReportConfig
    data: TableReportData[]
    unit?: string
    selector(d: TableReportData): number
    siblingSelector(d: TableReportData, index: number): number
}

function tryConvertToNumber(value: any) {
    const num = parseInt(value)
    if (!isNaN(num) && num.toString() === value) {
        return num
    }
    return value
}

function numericCompare(a: any, b: any) {
    a = tryConvertToNumber(a)
    b = tryConvertToNumber(b)
    if (typeof a === "number" && typeof b === "number" && !isNaN(a) && !isNaN(b)) {
        return a - b
    }
    return a.toString().localeCompare(b.toString())
}

function ComponentTable(props: ComponentTableProps) {
    const series = [...new Set(props.data.map(d => d.series))].sort(numericCompare)
    const scales = [...new Set(props.data.map(d => d.scale))].sort(numericCompare)
    const seriesFormatter = formatter(props.config.seriesFormatter)
    const scaleFormatter = formatter(props.config.scaleFormatter)
    const chartData: Record<string, string | number>[] = scales.map(scale => ({ scale }))
    props.data.forEach(d => {
        const matching = chartData.find(item => item.scale === d.scale)
        if (matching) {
            matching[d.series] = props.selector(d)
        }
    })
    const [minMaxDomain, setMinMaxDomain] = useState(false)
    const tickSuffix = props.unit && props.unit.length <= 4 ? props.unit : ""
    const commonChartElements = [
        <CartesianGrid key="grid" strokeDasharray="3 3" />,
        <YAxis
            key="yaxis"
            width={80}
            yAxisId={0}
            tick={{ fontSize: 12 }}
            tickFormatter={value => value.toLocaleString(undefined, { maximumFractionDigits: 2 }) + tickSuffix}
            domain={minMaxDomain ? ["dataMin", "dataMax"] : undefined}
            label={
                props.unit && props.unit.length > 4
                    ? {
                          value: props.unit.trim(),
                          position: "insideLeft",
                          angle: -90,
                          style: { textAnchor: "middle" },
                      }
                    : undefined
            }
        />,
        <Legend
            key="legend"
            iconType="line"
            payload={series.map((s, i) => ({
                id: s,
                type: "line",
                color: colors[i % colors.length],
                value: seriesFormatter(s),
            }))}
            align="center"
        />,
    ]
    return (
        <Level style={{ padding: "50px" }}>
            <LevelItem style={{ width: "100%" }}>
                <TableComposable variant="compact">
                    <Thead>
                        <Tr>
                            <Th></Th>
                            <Th>{props.config.scaleDescription}</Th>
                            {series.map(s => (
                                <Th key={s}>{seriesFormatter(s)}</Th>
                            ))}
                        </Tr>
                    </Thead>
                    <Tbody>
                        {scales.map(sc => (
                            <Tr key={sc}>
                                <Td>
                                    <Tooltip content="Set as baseline">
                                        <Checkbox
                                            id={sc}
                                            isChecked={sc === props.baseline}
                                            onChange={checked => props.onBaselineChange(checked ? sc : undefined)}
                                        />
                                    </Tooltip>
                                </Td>
                                <Th>{scaleFormatter(sc)}</Th>
                                {series.map(s => (
                                    <Td key={s}>
                                        <DataView
                                            config={props.config}
                                            data={props.data.find(d => d.series === s && d.scale === sc)}
                                            baseline={props.data.find(
                                                d => d.series === s && d.scale === props.baseline
                                            )}
                                            unit={props.unit}
                                            selector={props.selector}
                                            siblingSelector={props.siblingSelector}
                                        />
                                    </Td>
                                ))}
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            </LevelItem>
            <LevelItem style={{ width: "100%", height: "400px", position: "relative", padding: "50px 0px" }}>
                <div className="chartSwitch nonPrintable">
                    <Switch
                        label="Min-max Y axis"
                        labelOff="Natural Y axis"
                        isChecked={minMaxDomain}
                        onChange={setMinMaxDomain}
                    />
                </div>
                <ResponsiveContainer width="100%" height="100%">
                    {scales.length > 1 ? (
                        <LineChart data={chartData} style={{ userSelect: "none" }}>
                            {commonChartElements}
                            <XAxis
                                allowDataOverflow={true}
                                name={props.config.scaleDescription}
                                type="category"
                                textAnchor="end"
                                height={50}
                                dataKey="scale"
                                tick={{ fontSize: 12 }}
                                padding={{ left: 16, right: 16 }}
                            />
                            ,
                            {series.map((s, i) => (
                                <Line
                                    type="linear"
                                    key={s}
                                    dataKey={s}
                                    stroke={colors[i % colors.length]}
                                    isAnimationActive={false}
                                />
                            ))}
                        </LineChart>
                    ) : (
                        <BarChart data={chartData} style={{ userSelect: "none" }}>
                            {commonChartElements}
                            {series.map((s, i) => (
                                <Bar
                                    key={s}
                                    dataKey={s}
                                    maxBarSize={80}
                                    fill={colors[i % colors.length]}
                                    isAnimationActive={false}
                                />
                            ))}
                        </BarChart>
                    )}
                </ResponsiveContainer>
            </LevelItem>
        </Level>
    )
}

function MarkdownCheatSheetLink() {
    return (
        <Tooltip position="right" content={<span>Markdown Cheat Sheet</span>}>
            <a
                style={{ padding: "5px 8px" }}
                target="_blank"
                rel="noopener noreferrer"
                href="https://www.markdownguide.org/cheat-sheet/"
            >
                <HelpIcon /> Markdown Cheat Sheet
            </a>
        </Tooltip>
    )
}

type CommentProps = {
    editable?: boolean
    text: string
    onUpdate(text: string): Promise<unknown>
}

function Comment(props: CommentProps) {
    const [edit, setEdit] = useState(false)
    const [text, setText] = useState(props.text)
    const [updating, setUpdating] = useState(false)
    // last text we've successfully updated to; this prevents the need to re-render whole report
    const [updated, setUpdated] = useState(props.text)
    useEffect(() => setUpdated(props.text), [props.text])
    if (!edit) {
        return (
            <div className="reportComment markdown-body">
                <ReactMarkdown>{text}</ReactMarkdown>
                {props.editable && (
                    <Button className="reportCommentEdit nonPrintable" variant="link" onClick={() => setEdit(true)}>
                        <EditIcon />
                        {text ? " Edit" : " Add"} comment
                    </Button>
                )}
            </div>
        )
    } else {
        return (
            <div className="reportComment">
                <TextArea
                    id="comment"
                    isDisabled={updating}
                    onChange={setText}
                    autoResize={true}
                    resizeOrientation="vertical"
                    value={text}
                />
                <ActionGroup>
                    <Button
                        variant="primary"
                        onClick={() => {
                            setUpdating(true)
                            props
                                .onUpdate(text)
                                .then(() => setUpdated(text))
                                .finally(() => {
                                    setUpdating(false)
                                    setEdit(false)
                                })
                        }}
                        isDisabled={updating}
                    >
                        {text ? "Save" : "Delete"}
                    </Button>
                    <Button
                        variant="secondary"
                        onClick={() => {
                            setText(updated)
                            setEdit(false)
                        }}
                        isDisabled={updating}
                    >
                        Cancel
                    </Button>
                    <MarkdownCheatSheetLink />
                </ActionGroup>
            </div>
        )
    }
}

type TableReportViewProps = {
    report: TableReport
    editable?: boolean
}

function update(
    report: TableReport,
    comment: ReportComment | undefined,
    text: string,
    level: number,
    category?: string,
    componentId?: number
) {
    if (comment === undefined) {
        comment = {
            id: -1,
            level,
            category,
            componentId,
            comment: text,
        }
        return Api.reportServiceUpdateComment(report.id, comment).then(c => {
            if (c) report.comments.push(c as ReportComment)
        })
    } else {
        comment.comment = text
        return Api.reportServiceUpdateComment(report.id, comment).then(c => {
            if (!c) {
                const index = report.comments.findIndex(cmt => cmt.id === comment?.id)
                if (index >= 0) {
                    report.comments.splice(index, 1)
                }
            }
        })
    }
}

function selectByKey(value: any, key: string) {
    if (typeof value == "object") {
        return value[key]
    } else {
        return value
    }
}

export default function TableReportView(props: TableReportViewProps) {
    const config = props.report.config
    const categories = [...new Set(props.report.data.map(d => d.category))].sort(numericCompare)
    const singleCategory = categories.length === 0 || (categories.length === 1 && categories[0] === "")
    const categoryFormatter = formatter(config.categoryFormatter)
    const comment0 = props.report.comments.find(c => c.level === 0)

    const history = useHistory()
    const queryParams = new URLSearchParams(history.location.search)
    const [baseline, setBaseline] = useState<string | undefined>(queryParams.get("baseline") || undefined)
    const onBaselineChange = (scale: string | undefined) => {
        setBaseline(scale)
        history.replace(history.location.pathname + (scale ? "?baseline=" + scale : ""))
    }

    return (
        <div>
            <Title headingLevel="h1">{config.title}</Title>
            Created on {formatDateTime(props.report.created)} from runs in test{" "}
            {props.report.config.test ? (
                <NavLink to={"/test/" + props.report.config.test.id}>
                    {props.report.config.test.name + " (" + props.report.config.test.id + ")"}
                </NavLink>
            ) : (
                "<deleted test>"
            )}
            <Comment
                text={comment0?.comment || ""}
                editable={props.editable}
                onUpdate={text => update(props.report, comment0, text, 0)}
            />
            {categories.map((cat, i1) => {
                const comment1 = props.report.comments.find(c => c.level === 1 && c.category === cat)
                return (
                    <React.Fragment key={i1}>
                        {singleCategory || <Title headingLevel="h2">Category: {categoryFormatter(cat)}</Title>}
                        <Comment
                            text={comment1?.comment || ""}
                            editable={props.editable}
                            onUpdate={text => update(props.report, comment1, text, 1, cat)}
                        />
                        {config.components.map((comp, i2) => {
                            const comment2 = props.report.comments.find(
                                c => c.level === 2 && c.category === cat && c.componentId === comp.id
                            )
                            const categoryData = props.report.data.filter(d => d.category === cat)
                            if (categoryData.some(d => typeof d.values[i2] == "object")) {
                                const set = new Set<string>()
                                categoryData.forEach(d => {
                                    const componentData = d.values[i2]
                                    if (typeof componentData == "object") {
                                        const tmp = componentData || {}
                                        const keys = [...Object.keys(tmp)]
                                        keys.forEach(k => set.add(k))
                                    } else {
                                        set.add("")
                                    }
                                })
                                return [...set].sort().map(key => (
                                    <React.Fragment key={i2 + "/" + key}>
                                        <Title headingLevel="h3">
                                            {comp.name}: {key}
                                        </Title>
                                        <Comment
                                            text={comment2?.comment || ""}
                                            editable={props.editable}
                                            onUpdate={text => update(props.report, comment2, text, 2, cat, comp.id)}
                                        />
                                        <ComponentTable
                                            baseline={baseline}
                                            onBaselineChange={onBaselineChange}
                                            config={config}
                                            data={categoryData}
                                            unit={comp.unit}
                                            selector={d => selectByKey(d.values[i2], key)}
                                            siblingSelector={(d, index) => selectByKey(d.values[index], key)}
                                        />
                                    </React.Fragment>
                                ))
                            } else {
                                return (
                                    <React.Fragment key={i2}>
                                        <Title headingLevel="h3">{comp.name}</Title>
                                        <Comment
                                            text={comment2?.comment || ""}
                                            editable={props.editable}
                                            onUpdate={text => update(props.report, comment2, text, 2, cat, comp.id)}
                                        />
                                        <ComponentTable
                                            baseline={baseline}
                                            onBaselineChange={onBaselineChange}
                                            config={config}
                                            data={categoryData}
                                            unit={comp.unit}
                                            selector={d => d.values[i2]}
                                            siblingSelector={(d, index) => d.values[index]}
                                        />
                                    </React.Fragment>
                                )
                            }
                        })}
                    </React.Fragment>
                )
            })}
        </div>
    )
}
