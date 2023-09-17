import { useCallback } from "react"
import Api from "../../api"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"
import { ActionLog } from "../../generated/models/ActionLog"
import { formatDateTime } from "../../utils"

type ActionLogModalProps = {
    testId: number
} & CommonLogModalProps

export default function ActionLogModal(props: ActionLogModalProps) {
    const fetchCount = useCallback((level: number | undefined) => Api.logServiceGetActionLogCount(props.testId, level), [props.testId])
    const fetchLogs = useCallback(
        (level: number | undefined, page: number | undefined, limit: number | undefined) =>
            Api.logServiceGetActionLog(props.testId, level, limit, page).then(response =>
                (response as ActionLog[]).map(log => ({
                    cells: [
                        { title: <LogLevelIcon level={log.level} /> },
                        { title: formatDateTime(log.timestamp) },
                        { title: log.event },
                        { title: log.type },
                        { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                    ],
                }))
            ),
        [props.testId]
    )
    const deleteLogs = useCallback((from: number | undefined, to: number | undefined) => Api.logServiceDeleteActionLogs(props.testId, from, to), [props.testId])
    return (
        <LogModal
            {...props}
            fetchCount={fetchCount}
            fetchLogs={fetchLogs}
            deleteLogs={deleteLogs}
            columns={["Level", "Timestamp", "Event", "Type", "Message"]}
        />
    )
}
