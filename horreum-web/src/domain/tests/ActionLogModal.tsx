import { useCallback } from "react"
import {logApi} from "../../api"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"
import { ActionLog } from "../../generated/models/ActionLog"
import { formatDateTime } from "../../utils"

type ActionLogModalProps = {
    testId: number
} & CommonLogModalProps

export default function ActionLogModal(props: ActionLogModalProps) {
    const fetchCount = useCallback(level => logApi.getActionLogCount(props.testId, level), [props.testId])
    const fetchLogs = useCallback(
        (level, page, limit) =>
            logApi.getActionLog(props.testId, level, limit, page).then(response =>
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
    const deleteLogs = useCallback((from, to) => logApi.deleteActionLogs(props.testId, from, to), [props.testId])
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
