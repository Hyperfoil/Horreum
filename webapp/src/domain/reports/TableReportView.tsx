import React, { useEffect, useState } from "react"

import { ActionGroup, Button, Level, LevelItem, Popover, Switch, TextArea, Title } from "@patternfly/react-core"
import { TableComposable, Thead, Tbody, Tr, Th, Td } from "@patternfly/react-table"
import { EditIcon } from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, XAxis, YAxis } from "recharts"

import { RunData, TableReport, TableReportConfig, ReportComment, updateComment } from "./api"
import { formatDateTime } from "../../utils"
import "./TableReportView.css"

function formatter(func: string | undefined) {
    // eslint-disable-next-line
    return func ? new Function(func) : (x: string) => x
}

type RunDataViewProps = {
    config: TableReportConfig
    data?: RunData
    index: number
}

function RunDataView(props: RunDataViewProps) {
    if (props.data === undefined) {
        return <>(no data)</>
    }
    return (
        <Popover
            headerContent={
                <>
                    Run <NavLink to={"/run/" + props.data.runId}>{props.data.runId}</NavLink>
                </>
            }
            bodyContent={
                <TableComposable variant="compact">
                    <Tbody>
                        {props.config.components.map((c, i) => (
                            <Tr key={i}>
                                <Th>{c.name}</Th>
                                <Td>{props.data?.values[i]}</Td>
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            }
        >
            <Button variant="link">{props.data.values[props.index]}</Button>
        </Popover>
    )
}

type ComponentTableProps = {
    config: TableReportConfig
    data: RunData[]
    index: number
}

const colors = ["#4caf50", "#FF0000", "#CC0066", "#0066FF", "#42a5f5", "#f1c40f"]

function ComponentTable(props: ComponentTableProps) {
    const series = [...new Set(props.data.map(d => d.series))].sort()
    const labels = [...new Set(props.data.map(d => d.label))].sort()
    const seriesFormatter = formatter(props.config.seriesFormatter)
    const labelFormatter = formatter(props.config.labelFormatter)
    const chartData: Record<string, string | number>[] = labels.map(label => ({ label }))
    props.data.forEach(d => {
        const matching = chartData.find(item => item.label === d.label)
        if (matching) {
            matching[d.series] = d.values[props.index]
        }
    })
    const [minMaxDomain, setMinMaxDomain] = useState(false)
    const commonChartElements = [
        <CartesianGrid strokeDasharray="3 3" />,
        <YAxis
            width={80}
            yAxisId={0}
            tick={{ fontSize: 12 }}
            domain={minMaxDomain ? ["dataMin", "dataMax"] : undefined}
        />,
        <Legend
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
        <Level>
            <LevelItem style={{ width: "50%" }}>
                <TableComposable variant="compact">
                    <Thead>
                        <Tr>
                            <Th></Th>
                            {series.map(s => (
                                <Th key={s}>{seriesFormatter(s)}</Th>
                            ))}
                        </Tr>
                    </Thead>
                    <Tbody>
                        {labels.map(l => (
                            <Tr key={l}>
                                <Th>{labelFormatter(l)}</Th>
                                {series.map(s => (
                                    <Td key={s}>
                                        <RunDataView
                                            config={props.config}
                                            data={props.data.find(d => d.series === s && d.label === l)}
                                            index={props.index}
                                        />
                                    </Td>
                                ))}
                            </Tr>
                        ))}
                    </Tbody>
                </TableComposable>
            </LevelItem>
            <LevelItem style={{ width: "50%", height: "300px", position: "relative" }}>
                <div className="chartSwitch nonPrintable">
                    <Switch
                        label="Min-max Y axis"
                        labelOff="Natural Y axis"
                        isChecked={minMaxDomain}
                        onChange={setMinMaxDomain}
                    />
                </div>
                <ResponsiveContainer width="100%" height="100%">
                    {labels.length > 1 ? (
                        <LineChart data={chartData} style={{ userSelect: "none" }}>
                            {commonChartElements}
                            <XAxis
                                allowDataOverflow={true}
                                type="category"
                                textAnchor="end"
                                height={50}
                                dataKey="label"
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
    if (!props.editable) {
        return props.text ? <div className="reportComment">{props.text}</div> : null
    }
    if (edit) {
        return (
            <div className="reportComment">
                <TextArea
                    id="comment"
                    style={{ width: "100%" }}
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
                </ActionGroup>
            </div>
        )
    } else {
        return (
            <div className="reportComment">
                <Button className="reportCommentEdit nonPrintable" variant="link" onClick={() => setEdit(true)}>
                    {text ? "Edit" : "Add"} comment <EditIcon />
                </Button>
                <div>{text}</div>
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
        return updateComment(report.id, comment).then(c => {
            if (c) report.comments.push(c as ReportComment)
        })
    } else {
        comment.comment = text
        return updateComment(report.id, comment).then(c => {
            if (!c) {
                const index = report.comments.findIndex(cmt => cmt.id === comment?.id)
                if (index >= 0) {
                    report.comments.splice(index, 1)
                }
            }
        })
    }
}

export default function TableReportView(props: TableReportViewProps) {
    const config = props.report.config
    const categories = [...new Set(props.report.runData.map(d => d.category))].sort()
    const singleCategory = categories.length === 0 || (categories.length === 1 && categories[0] === "")
    const categoryFormatter = formatter(config.categoryFormatter)
    const comment0 = props.report.comments.find(c => c.level === 0)

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
                            return (
                                <React.Fragment key={i2}>
                                    <Title headingLevel="h3">{comp.name}</Title>
                                    <Comment
                                        text={comment2?.comment || ""}
                                        editable={props.editable}
                                        onUpdate={text => update(props.report, comment2, text, 2, cat, comp.id)}
                                    />
                                    <ComponentTable
                                        config={config}
                                        data={props.report.runData.filter(d => d.category === cat)}
                                        index={i2}
                                    />
                                </React.Fragment>
                            )
                        })}
                    </React.Fragment>
                )
            })}
        </div>
    )
}
