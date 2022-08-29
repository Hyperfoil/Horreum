import { useCallback } from "react"
import Api from "../../api"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"
import { ActionLog } from "../../generated/models/ActionLog"
import { formatDateTime } from "../../utils"

type ActionLogModalProps = {
    testId: number
} & CommonLogModalProps

export default function ActionLogModal(props: ActionLogModalProps) {
    const fetchCount = useCallback(() => Api.logServiceGetActionLogCount(props.testId), [props.testId])
    const fetchLogs = useCallback(
        (page, limit) =>
            Api.logServiceGetActionLog(props.testId, limit, page).then(response =>
                (response as ActionLog[]).map(log => ({
                    cells: [
                        { title: <LogLevelIcon level={log.level} /> },
                        { title: formatDateTime(log.timestamp * 1000) },
                        { title: log.event },
                        { title: log.type },
                        { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                    ],
                }))
            ),
        [props.testId]
    )
    const deleteLogs = useCallback((from, to) => Api.logServiceDeleteActionLogs(props.testId, from, to), [props.testId])
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
