import { useCallback } from "react"
import { NavLink } from "react-router-dom"

import Api, { TransformationLog } from "../../api"
import { formatDateTime } from "../../utils"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"

type TransformationLogModalProps = {
    testId: number
    runId?: number
} & CommonLogModalProps

export default function TransformationLogModal(props: TransformationLogModalProps) {
    const fetchCount = useCallback(
        () => Api.logServiceGetTransformationLogCount(props.testId, props.runId),
        [props.testId]
    )
    const fetchRows = useCallback(
        (page, limit) =>
            Api.logServiceGetTransformationLog(props.testId, props.runId, page, limit).then(response =>
                (response as TransformationLog[]).map(log => ({
                    cells: [
                        { title: <LogLevelIcon level={log.level} /> },
                        { title: formatDateTime(log.timestamp * 1000) },
                        {
                            title: <NavLink to={`/run/${log.runId}#run`}>{log.runId}</NavLink>,
                        },
                        { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                    ],
                }))
            ),
        [props.testId]
    )
    const deleteFromTo = useCallback(
        (from, to) => Api.logServiceDeleteTransformationLogs(props.testId, props.runId, from, to),
        [props.testId]
    )
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
