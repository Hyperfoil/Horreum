import {ReactElement, useContext, useEffect, useMemo, useState} from "react"
import {
    Button,
    Bullseye,
    EmptyState,
    EmptyStateBody,
    Flex,
    FlexItem,
    Modal,
    Pagination,
    Spinner, EmptyStateHeader,
    } from "@patternfly/react-core"
import {
    CheckCircleIcon,
    ExclamationCircleIcon,
    ExclamationTriangleIcon,
    InfoCircleIcon,
} from "@patternfly/react-icons"

import TimeRangeSelect, { TimeRange } from "./TimeRangeSelect"
import ConfirmDeleteModal from "./ConfirmDeleteModal"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
import {IRow, IRowCell, OuterScrollContainer, Table, Tbody, Td, Th, Thead, Tr} from "@patternfly/react-table";
import {SimpleSelect} from "@patternfly/react-templates";

export type CommonLogModalProps = {
    title: string
    emptyMessage: string
    isOpen: boolean
    onClose(): void
}

export function LogLevelIcon(props: { level: number }) {
    const levels = [
        <CheckCircleIcon style={{ fill: "var(--pf-v5-global--success-color--100)" }} />,
        <InfoCircleIcon style={{ fill: "var(--pf-v5-global--info-color--100)" }} />,
        <ExclamationTriangleIcon style={{ fill: "var(--pf-v5-global--warning-color--100)" }} />,
        <ExclamationCircleIcon style={{ fill: "var(--pf-v5-global--danger-color--100)" }} />,
    ]
    return levels[props.level]
}

const LOG_LEVELS = [
    <>
        <LogLevelIcon level={0} /> DEBUG
    </>,
    <>
        <LogLevelIcon level={1} /> INFO
    </>,
    <>
        <LogLevelIcon level={2} /> WARNING
    </>,
    <>
        <LogLevelIcon level={3} /> ERROR
    </>,
].reduce((acc, el, i) => {
    acc[i.toString()] = el
    return acc
}, {} as Record<string, ReactElement>)

type LogModalProps = {
    columns: string[]
    fetchCount(level: number): Promise<number>
    fetchLogs(level: number, page: number, limit: number): Promise<IRow[]>
    deleteLogs?(from?: number, to?: number): Promise<unknown>
} & CommonLogModalProps

export default function LogModal(props: LogModalProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [level, setLevel] = useState(1)
    const [count, setCount] = useState(0)
    const [page, setPage] = useState(0)
    const [limit, setLimit] = useState(25)
    const [loading, setLoading] = useState(false)
    const [rows, setRows] = useState<IRow[]>([])
    const [deleteRange, setDeleteRange] = useState<TimeRange>()
    const [deleteRequest, setDeleteRequest] = useState<TimeRange>()
    const [updateCounter, setUpdateCounter] = useState(0)
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        props.fetchCount(level).then(
            response => setCount(typeof response === "number" ? response : parseInt(response)),
            error => {
                alerting.dispatchError(error,"LOG", "Cannot get logs.")
                props.onClose()
            }
        )
    }, [props.isOpen, props.fetchCount, updateCounter, level])
    useEffect(() => {
        if (!props.isOpen) {
            return
        }
        setLoading(true)
        props
            .fetchLogs(level, page, limit)
            .then(setRows)
            .catch(error => {
                alerting.dispatchError(error, "LOG", "Cannot get logs.")
                props.onClose()
            })
            .finally(() => setLoading(false))
    }, [page, limit, props.isOpen, props.fetchLogs, updateCounter, level])
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
                    <EmptyStateHeader titleText="No logs" headingLevel="h4" />
                    <EmptyStateBody>
                        {props.emptyMessage}
                        <div style={{ marginTop: "16px" }}>
                            {level == 1 && <Button onClick={() => setLevel(0)}>Show debug logs</Button>}
                            {level > 1 && <Button onClick={() => setLevel(1)}>Show info logs</Button>}
                        </div>
                    </EmptyStateBody>
                </EmptyState>
            )}
            {!loading && count > 0 && (
                <>
                    {props.deleteLogs && (
                        <Flex>
                            <FlexItem>
                                <SimpleSelect
                                    initialOptions={Object.entries(LOG_LEVELS).map(([name, title]) => (
                                        {value: name, content: title, selected: name === level.toString()}
                                    ))}
                                    selected={level.toString()}
                                    onSelect={(_, value) => setLevel(value as number)}
                                />
                            </FlexItem>
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
                                        alerting.dispatchError(error,"LOGS DELETE", "Deleting logs failed")
                                        props.onClose()
                                    }
                                )
                            } else {
                                return Promise.resolve()
                            }
                        }}
                    />
                    <OuterScrollContainer style={{ overflowY:"auto", height: "80vh" }}>
                        <Table aria-label="Simple Table" variant="compact">
                            <Thead>
                                <Tr>
                                    {props.columns.map((col, index) =>
                                        <Th key={index}>{col}</Th>
                                    )}
                                </Tr>
                            </Thead>
                            <Tbody>
                                {rows.map((row, index) =>
                                    <Tr key={index}>
                                        {row.cells?.map((cell, index) =>
                                            <Td key={index}>{(cell as IRowCell).title}</Td>
                                        )}
                                    </Tr>
                                )}
                            </Tbody>
                        </Table>
                    </OuterScrollContainer>
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
