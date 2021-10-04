import React, { useState, useEffect } from "react"
import { useDispatch } from "react-redux"
import { IRow, Table, TableHeader, TableBody } from "@patternfly/react-table"
import { EmptyState, EmptyStateBody, Modal, Pagination, Title } from "@patternfly/react-core"
import {
    CheckCircleIcon,
    ExclamationCircleIcon,
    ExclamationTriangleIcon,
    InfoCircleIcon,
} from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import { fetchLog, getLogCount } from "../alerting/api"
import { alertAction } from "../../alerts"
import { formatDateTime } from "../../utils"
import "./CalculationLogModal.css"

type CalculationLogModalProps = {
    isOpen: boolean
    onClose(): void
    testId: number
}

type CalculationLog = {
    id: number
    testId: number
    runId: number
    level: number
    timestamp: number
    message: string
}

const level = [
    <CheckCircleIcon style={{ fill: "var(--pf-global--success-color--100)" }} />,
    <InfoCircleIcon style={{ fill: "var(--pf-global--info-color--100)" }} />,
    <ExclamationTriangleIcon style={{ fill: "var(--pf-global--warning-color--100)" }} />,
    <ExclamationCircleIcon style={{ fill: "var(--pf-global--danger-color--100)" }} />,
]

export default function CalculationLogModal(props: CalculationLogModalProps) {
    const [count, setCount] = useState(0)
    const [page, setPage] = useState(0)
    const [limit, setLimit] = useState(25)
    const [rows, setRows] = useState<IRow[]>([])
    const dispatch = useDispatch()
    useEffect(() => {
        if (props.isOpen) {
            getLogCount(props.testId).then(
                response => setCount(response),
                error => dispatch(alertAction("CALCULATIONS_LOG", "Cannot get regression calculation logs.", error))
            )
        }
    }, [props.isOpen, props.testId, dispatch])
    useEffect(() => {
        if (props.isOpen) {
            fetchLog(props.testId, page, limit).then(
                response =>
                    setRows(
                        (response as CalculationLog[]).map(log => ({
                            cells: [
                                { title: level[log.level] },
                                { title: formatDateTime(log.timestamp) },
                                { title: <NavLink to={`/run/${log.runId}`}>{log.runId}</NavLink> },
                                { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                            ],
                        }))
                    ),
                error => dispatch(alertAction("CALCULATIONS_LOG", "Cannot get regression calculation logs.", error))
            )
        }
    }, [page, limit, props.isOpen, props.testId, dispatch])
    return (
        <Modal isOpen={props.isOpen} onClose={props.onClose} title="Calculation log events" showClose={true}>
            {count === 0 && (
                <EmptyState>
                    <Title headingLevel="h4" size="lg">
                        No logs
                    </Title>
                    <EmptyStateBody>
                        This test did not generate any logs. You can press 'Recalculate' to trigger recalculation of
                        datapoints.
                    </EmptyStateBody>
                </EmptyState>
            )}
            {count > 0 && (
                <>
                    <div className="forceOverflowY">
                        <Table
                            aria-label="Simple Table"
                            variant="compact"
                            cells={["Level", "Timestamp", "Run ID", "Message"]}
                            rows={rows}
                        >
                            <TableHeader />
                            <TableBody />
                        </Table>
                    </div>
                    <Pagination
                        itemCount={count}
                        perPage={limit}
                        page={page + 1}
                        onSetPage={(e, p) => setPage(p - 1)}
                        onPerPageSelect={(e, pp) => setLimit(pp)}
                    />
                </>
            )}
        </Modal>
    )
}
