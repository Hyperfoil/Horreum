import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import { IRow, Table, TableHeader, TableBody } from "@patternfly/react-table"
import { Button, EmptyState, EmptyStateBody, Flex, FlexItem, Modal, Pagination, Title } from "@patternfly/react-core"
import {
    CheckCircleIcon,
    ExclamationCircleIcon,
    ExclamationTriangleIcon,
    InfoCircleIcon,
} from "@patternfly/react-icons"
import { NavLink } from "react-router-dom"

import TimeRangeSelect, { TimeRange } from "../../components/TimeRangeSelect"
import ConfirmDeleteModal from "../../components/ConfirmDeleteModal"
import { fetchApi } from "../../services/api/index"
import { alertAction } from "../../alerts"
import { formatDateTime } from "../../utils"
import "./DatasetLogModal.css"

type DatasetLogModalProps = {
    isOpen: boolean
    onClose(): void
    testId: number
    source: string
}

type DatasetLog = {
    id: number
    testId: number
    runId: number
    datasetId: number
    datasetOrdinal: number
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

function fetchLog(testId: number, source: string, page?: number, limit?: number) {
    return fetchApi(`/api/log/${source}/${testId}?page=${page ? page : 0}&limit=${limit ? limit : 25}`, null, "get")
}

function getLogCount(testId: number, source: string) {
    return fetchApi(`/api/log/${source}/${testId}/count`, null, "get")
}

function deleteLogs(testId: number, source: string, fromMs?: number, toMs?: number) {
    return fetchApi(
        `/api/log/${source}/${testId}?${[fromMs ? "from=" + fromMs : undefined, toMs ? "to=" + toMs : undefined]
            .filter(p => p !== undefined)
            .join("&")}`,
        null,
        "delete"
    )
}

export default function DatasetLogModal(props: DatasetLogModalProps) {
    const [count, setCount] = useState(0)
    const [page, setPage] = useState(0)
    const [limit, setLimit] = useState(25)
    const [rows, setRows] = useState<IRow[]>([])
    const [deleteRange, setDeleteRange] = useState<TimeRange>()
    const [deleteRequest, setDeleteRequest] = useState<TimeRange>()
    const [updateCounter, setUpdateCounter] = useState(0)
    const dispatch = useDispatch()
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        getLogCount(props.testId, props.source).then(
            response => setCount(response),
            error => dispatch(alertAction("CALCULATIONS_LOG", "Cannot get change detection calculation logs.", error))
        )
    }, [props.isOpen, props.testId, props.source, dispatch, updateCounter])
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        fetchLog(props.testId, props.source, page, limit).then(
            response =>
                setRows(
                    (response as DatasetLog[]).map(log => ({
                        cells: [
                            { title: level[log.level] },
                            { title: formatDateTime(log.timestamp * 1000) },
                            {
                                title: (
                                    <NavLink to={`/run/${log.runId}#dataset${log.datasetOrdinal}`}>
                                        {log.runId}/{log.datasetOrdinal + 1}
                                    </NavLink>
                                ),
                            },
                            { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                        ],
                    }))
                ),
            error => dispatch(alertAction("CALCULATIONS_LOG", "Cannot get change detection calculation logs.", error))
        )
    }, [page, limit, props.isOpen, props.testId, props.source, dispatch, updateCounter])
    const timeRangeOptions: TimeRange[] = useMemo(
        () => [
            { toString: () => "delete all" },
            { from: undefined, to: Date.now() - 86_400_000, toString: () => "24 hours" },
            { from: undefined, to: Date.now() - 7 * 86_400_000, toString: () => "one week" },
            { from: undefined, to: Date.now() - 31 * 86_400_000, toString: () => "one month" },
        ],
        []
    )
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
                    <Flex>
                        <FlexItem>
                            <Button onClick={() => setDeleteRequest(deleteRange)}>Delete logs older than...</Button>
                        </FlexItem>
                        <FlexItem>
                            <TimeRangeSelect
                                selection={deleteRange}
                                onSelect={setDeleteRange}
                                options={timeRangeOptions}
                            />
                        </FlexItem>
                    </Flex>
                    <ConfirmDeleteModal
                        description={
                            deleteRequest && (deleteRequest.from || deleteRequest.to)
                                ? "logs older than " + deleteRequest
                                : "all logs"
                        }
                        isOpen={deleteRequest !== undefined}
                        onClose={() => setDeleteRequest(undefined)}
                        onDelete={() => {
                            if (deleteRequest) {
                                return deleteLogs(
                                    props.testId,
                                    props.source,
                                    deleteRequest.from,
                                    deleteRequest.to
                                ).then(
                                    () => setUpdateCounter(updateCounter + 1),
                                    error => {
                                        dispatch(alertAction("LOGS DELETE", "Deleting logs failed", error))
                                        props.onClose()
                                    }
                                )
                            } else {
                                return Promise.resolve()
                            }
                        }}
                    />
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
