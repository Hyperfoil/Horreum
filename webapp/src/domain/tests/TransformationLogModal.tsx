import { useCallback } from "react"
import { NavLink } from "react-router-dom"

import { fetchApi } from "../../services/api/index"
import { formatDateTime } from "../../utils"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "./LogModal"

type TransformationLog = {
    id: number
    testId: number
    runId: number
    level: number
    timestamp: number
    message: string
}

function fetchLog(testId: number, runId?: number, page?: number, limit?: number) {
    return fetchApi(
        `/api/log/transformation/${testId}?page=${page ? page : 0}&limit=${limit ? limit : 25}${
            runId !== undefined ? "&runId=" + runId : ""
        }`,
        null,
        "get"
    )
}

function getLogCount(testId: number, runId?: number) {
    return fetchApi(
        `/api/log/transformation/${testId}/count${runId !== undefined ? "?runId=" + runId : ""}`,
        null,
        "get"
    )
}

function deleteLogs(testId: number, runId?: number, fromMs?: number, toMs?: number) {
    return fetchApi(
        `/api/log/transformation/${testId}?${[
            fromMs ? "from=" + fromMs : undefined,
            toMs ? "to=" + toMs : undefined,
            runId !== undefined ? "runId=" + runId : undefined,
        ]
            .filter(p => p !== undefined)
            .join("&")}`,
        null,
        "delete"
    )
}

type TransformationLogModalProps = {
    testId: number
    runId?: number
} & CommonLogModalProps

export default function TransformationLogModal(props: TransformationLogModalProps) {
    const fetchCount = useCallback(() => getLogCount(props.testId, props.runId), [props.testId])
    const fetchRows = useCallback(
        (page, limit) =>
            fetchLog(props.testId, props.runId, page, limit).then(response =>
                (response as TransformationLog[]).map(log => ({
                    cells: [
                        { title: <LogLevelIcon level={log.level} /> },
                        { title: formatDateTime(log.timestamp * 1000) },
                        {
                            title: <NavLink to={`/run/${log.runId}`}>{log.runId}</NavLink>,
                        },
                        { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                    ],
                }))
            ),
        [props.testId]
    )
    const deleteFromTo = useCallback((from, to) => deleteLogs(props.testId, props.runId, from, to), [props.testId])
    return (
        <LogModal
            {...props}
            columns={["Level", "Timestamp", "Run ID", "Message"]}
            fetchCount={fetchCount}
            fetchLogs={fetchRows}
            deleteLogs={deleteFromTo}
        />
    )
}
