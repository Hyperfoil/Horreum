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
        () => Api.logServiceGetDatasetLogCount(props.source, props.testId, props.datasetId),
        [props.testId, props.source]
    )
    const fetchRows = useCallback(
        (page, limit) =>
            Api.logServiceGetDatasetLog(props.source, props.testId, props.datasetId, limit, page).then(response =>
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
        (from, to) => Api.logServiceDeleteDatasetLogs(props.source, props.testId, props.datasetId, from, to),
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
