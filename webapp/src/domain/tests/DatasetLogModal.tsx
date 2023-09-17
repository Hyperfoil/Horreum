import { useCallback } from "react"
import { NavLink } from "react-router-dom"

import { formatDateTime } from "../../utils"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"
import Api, { DatasetLog } from "../../api"

type DatasetLogModalProps = {
    testId: number
    source: string
    datasetId?: number
} & CommonLogModalProps

export default function DatasetLogModal(props: DatasetLogModalProps) {
    const fetchCount = useCallback(
        (        level: number | undefined) => Api.logServiceGetDatasetLogCount(props.source, props.testId, props.datasetId, level),
        [props.testId, props.datasetId, props.source, props.isOpen]
    )
    const fetchRows = useCallback(
        (level: number | undefined, page: number | undefined, limit: number | undefined) =>
            Api.logServiceGetDatasetLog(props.source, props.testId, props.datasetId, level, limit, page).then(
                response =>
                    (response as DatasetLog[]).map(log => ({
                        cells: [
                            { title: <LogLevelIcon level={log.level} /> },
                            { title: formatDateTime(log.timestamp) },
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
        [props.testId, props.datasetId, props.source]
    )
    const deleteFromTo = useCallback(
        (from: number | undefined, to: number | undefined) => Api.logServiceDeleteDatasetLogs(props.source, props.testId, props.datasetId, from, to),
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
