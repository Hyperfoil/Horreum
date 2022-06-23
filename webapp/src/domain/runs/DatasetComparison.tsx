import { useEffect, useRef, useState } from "react"
import { useDispatch } from "react-redux"
import {
    ActionGroup,
    Bullseye,
    Card,
    CardBody,
    CardHeader,
    EmptyState,
    EmptyStateBody,
    PageSection,
    Spinner,
} from "@patternfly/react-core"
import { expandable, ICell, IRow, Table, TableHeader, TableBody } from "@patternfly/react-table"
import { useHistory, NavLink } from "react-router-dom"
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, YAxis } from "recharts"

import Api from "../../api"
import { dispatchError } from "../../alerts"
import { colors } from "../../charts"

import PrintButton from "../../components/PrintButton"

export default function DatasetComparison() {
    const history = useHistory()
    const params = new URLSearchParams(history.location.search)
    const [loading, setLoading] = useState(false)
    const [headers, setHeaders] = useState<ICell[]>([])
    const [rows, setRows] = useState<IRow[]>([])

    window.document.title = "Dataset comparison: Horreum"

    const dispatch = useDispatch()
    useEffect(() => {
        setLoading(true)
        Promise.all(
            params.getAll("ds").map(ds => {
                const parts = ds.split("_")
                const id = parseInt(parts[0])
                return Api.datasetServiceLabelValues(id).then(values => ({
                    id,
                    runId: parseInt(parts[1]),
                    ordinal: parseInt(parts[2]),
                    values,
                }))
            })
        )
            .then(
                labels => {
                    labels.sort((a, b) => (b.runId - a.runId) * 1000 + (b.ordinal - a.ordinal))
                    const rows: any[][] = []
                    labels.forEach((item, index) => {
                        item.values.forEach(label => {
                            let row = rows.find(r => r[0] === label.name)
                            if (row === undefined) {
                                row = [label.name, ...labels.map(x => (x.id === item.id ? label.value : undefined))]
                                rows.push(row)
                            } else {
                                row[index + 1] = label.value
                            }
                        })
                    })
                    setHeaders([
                        { title: "Label name", cellFormatters: [expandable] },
                        ...labels.map(item => ({
                            title: (
                                <NavLink to={`/run/${item.runId}#dataset${item.ordinal}`}>
                                    {item.runId}/{item.ordinal + 1}
                                </NavLink>
                            ),
                        })),
                    ])
                    rows.sort((r1, r2) => r1[0].localeCompare(r2[0]))
                    const renderRows: IRow[] = []
                    rows.forEach(row => {
                        const numeric = row.every((value, i) => i === 0 || typeof value === "number")
                        renderRows.push({
                            isOpen: numeric ? false : undefined,
                            cells: row.map(value => ({
                                title: typeof value === "object" ? JSON.stringify(value) : value,
                            })),
                        })
                        if (numeric) {
                            renderRows.push({
                                parent: renderRows.length - 1,
                                cells: [
                                    "",
                                    {
                                        title: (
                                            <BarValuesChart
                                                values={row.slice(1)}
                                                legend={labels.map(item => `${item.runId}/${item.ordinal + 1}`)}
                                            />
                                        ),
                                        props: {
                                            colSpan: labels.length,
                                        },
                                    },
                                ],
                            })
                        }
                    })
                    setRows(renderRows)
                },
                e => dispatchError(dispatch, e, "LOAD_LABELS", "Failed to load labels for one of the datasets.")
            )
            .finally(() => setLoading(false))
    }, [])
    const componentRef = useRef<HTMLDivElement>(null)
    const empty = headers.length <= 1
    return (
        <PageSection>
            <Card>
                <CardHeader>
                    <ActionGroup>
                        <PrintButton printRef={componentRef} />
                    </ActionGroup>
                </CardHeader>
                <CardBody>
                    {loading && (
                        <Bullseye>
                            <Spinner size="xl" />
                        </Bullseye>
                    )}
                    {!loading && empty && (
                        <EmptyState>
                            <EmptyStateBody>No datasets have been loaded</EmptyStateBody>
                        </EmptyState>
                    )}
                    {!loading && !empty && (
                        <div ref={componentRef}>
                            <Table
                                aria-label="Label comparison"
                                variant="compact"
                                cells={headers}
                                rows={rows}
                                isExpandable={true}
                                onCollapse={(_, rowIndex, isOpen) => {
                                    rows[rowIndex].isOpen = isOpen
                                    setRows([...rows])
                                }}
                            >
                                <TableHeader />
                                <TableBody />
                            </Table>
                        </div>
                    )}
                </CardBody>
            </Card>
        </PageSection>
    )
}

type BarValuesChartProps = {
    values: number[]
    legend: string[]
}

function BarValuesChart(props: BarValuesChartProps) {
    const data: Record<string, number>[] = [{}]
    props.values.forEach((v, i) => {
        data[0][i] = v
    })

    return (
        <ResponsiveContainer width="100%" height={250}>
            <BarChart data={data} style={{ userSelect: "none" }}>
                <CartesianGrid key="grid" strokeDasharray="3 3" />,
                <YAxis
                    key="yaxis"
                    yAxisId={0}
                    tick={{ fontSize: 12 }}
                    tickFormatter={value => value.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                />
                ,
                {props.legend.map((name, i) => (
                    <Bar
                        key={i}
                        dataKey={i}
                        maxBarSize={80}
                        fill={colors[i % colors.length]}
                        isAnimationActive={false}
                        label={({ x, width, y, stroke, value }) => {
                            return (
                                <text x={x + width / 2} y={y} dy={-4} fill={stroke} fontSize={16} textAnchor="middle">
                                    {name}: {value}
                                </text>
                            )
                        }}
                    />
                ))}
            </BarChart>
        </ResponsiveContainer>
    )
}
