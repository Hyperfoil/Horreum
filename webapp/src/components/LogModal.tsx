import { useEffect, useMemo, useState } from "react"
import { useDispatch } from "react-redux"
import { IRow, Table, TableHeader, TableBody } from "@patternfly/react-table"
import {
    Button,
    Bullseye,
    EmptyState,
    EmptyStateBody,
    Flex,
    FlexItem,
    Modal,
    Pagination,
    Spinner,
    Title,
} from "@patternfly/react-core"
import {
    CheckCircleIcon,
    ExclamationCircleIcon,
    ExclamationTriangleIcon,
    InfoCircleIcon,
} from "@patternfly/react-icons"

import TimeRangeSelect, { TimeRange } from "./TimeRangeSelect"
import ConfirmDeleteModal from "./ConfirmDeleteModal"
import { alertAction } from "../alerts"
import "./LogModal.css"

export type CommonLogModalProps = {
    title: string
    emptyMessage: string
    isOpen: boolean
    onClose(): void
}

export function LogLevelIcon(props: { level: number }) {
    const levels = [
        <CheckCircleIcon style={{ fill: "var(--pf-global--success-color--100)" }} />,
        <InfoCircleIcon style={{ fill: "var(--pf-global--info-color--100)" }} />,
        <ExclamationTriangleIcon style={{ fill: "var(--pf-global--warning-color--100)" }} />,
        <ExclamationCircleIcon style={{ fill: "var(--pf-global--danger-color--100)" }} />,
    ]
    return levels[props.level]
}

type LogModalProps = {
    columns: string[]
    fetchCount(): Promise<number>
    fetchLogs(page: number, limit: number): Promise<IRow[]>
    deleteLogs?(from?: number, to?: number): Promise<unknown>
} & CommonLogModalProps

export default function LogModal(props: LogModalProps) {
    const [count, setCount] = useState(0)
    const [page, setPage] = useState(0)
    const [limit, setLimit] = useState(25)
    const [loading, setLoading] = useState(false)
    const [rows, setRows] = useState<IRow[]>([])
    const [deleteRange, setDeleteRange] = useState<TimeRange>()
    const [deleteRequest, setDeleteRequest] = useState<TimeRange>()
    const [updateCounter, setUpdateCounter] = useState(0)
    const dispatch = useDispatch()
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        props.fetchCount().then(
            response => setCount(response),
            error => dispatch(alertAction("LOG", "Cannot get logs.", error))
        )
    }, [props.isOpen, props.fetchCount, dispatch, updateCounter])
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        setLoading(true)
        props
            .fetchLogs(page, limit)
            .then(setRows)
            .catch(error => dispatch(alertAction("LOG", "Cannot get logs.", error)))
            .finally(() => setLoading(false))
    }, [page, limit, props.isOpen, props.fetchLogs, dispatch, updateCounter])
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
        <Modal isOpen={props.isOpen} onClose={props.onClose} title={props.title} showClose={true}>
            {loading && (
                <Bullseye>
                    <Spinner size="xl" />
                </Bullseye>
            )}
            {!loading && count === 0 && (
                <EmptyState>
                    <Title headingLevel="h4" size="lg">
                        No logs
                    </Title>
                    <EmptyStateBody>{props.emptyMessage}</EmptyStateBody>
                </EmptyState>
            )}
            {!loading && count > 0 && (
                <>
                    {props.deleteLogs && (
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
                    )}
                    <ConfirmDeleteModal
                        description={
                            deleteRequest && (deleteRequest.from || deleteRequest.to)
                                ? "logs older than " + deleteRequest
                                : "all logs"
                        }
                        isOpen={deleteRequest !== undefined}
                        onClose={() => setDeleteRequest(undefined)}
                        onDelete={() => {
                            if (deleteRequest && props.deleteLogs) {
                                return props.deleteLogs(deleteRequest.from, deleteRequest.to).then(
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
                        <Table aria-label="Simple Table" variant="compact" cells={props.columns} rows={rows}>
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
