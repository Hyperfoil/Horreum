import { useCallback } from "react"
import { NavLink } from "react-router-dom"

import { fetchApi } from "../../services/api/index"
import { formatDateTime } from "../../utils"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"

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

function fetchLog(testId: number, datasetId: number | undefined, source: string, page?: number, limit?: number) {
    return fetchApi(
        `/api/log/dataset/${source}/${testId}?page=${page ? page : 0}&limit=${limit ? limit : 25}${
            datasetId !== undefined ? "&datasetId=" + datasetId : ""
        }`,
        null,
        "get"
    )
}

function getLogCount(testId: number, datasetId: number | undefined, source: string) {
    return fetchApi(
        `/api/log/dataset/${source}/${testId}/count${datasetId !== undefined ? "?datasetId=" + datasetId : ""}`,
        null,
        "get"
    )
}

function deleteLogs(testId: number, datasetId: number | undefined, source: string, fromMs?: number, toMs?: number) {
    return fetchApi(
        `/api/log/dataset/${source}/${testId}?${[
            fromMs ? "from=" + fromMs : undefined,
            toMs ? "to=" + toMs : undefined,
            datasetId ? "datasetId=" + datasetId : undefined,
        ]
            .filter(p => p !== undefined)
            .join("&")}`,
        null,
        "delete"
    )
}

type DatasetLogModalProps = {
    testId: number
    source: string
    datasetId?: number
} & CommonLogModalProps

export default function DatasetLogModal(props: DatasetLogModalProps) {
    const fetchCount = useCallback(
        () => getLogCount(props.testId, props.datasetId, props.source),
        [props.testId, props.source]
    )
    const fetchRows = useCallback(
        (page, limit) =>
            fetchLog(props.testId, props.datasetId, props.source, page, limit).then(response =>
                (response as DatasetLog[]).map(log => ({
                    cells: [
                        { title: <LogLevelIcon level={log.level} /> },
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
        [props.testId, props.source]
    )
    const deleteFromTo = useCallback(
        (from, to) => deleteLogs(props.testId, props.datasetId, props.source, from, to),
        [props.testId, props.source]
    )
    return (
        <LogModal
            {...props}
            columns={["Level", "Timestamp", "Dataset", "Message"]}
            fetchCount={fetchCount}
            fetchLogs={fetchRows}
            deleteLogs={deleteFromTo}
        />
    )
}
