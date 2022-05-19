import { useMemo } from "react"

import { formatDateTime } from "../../utils"
import { ReportLog } from "./api"
import LogModal, { CommonLogModalProps, LogLevelIcon } from "../../components/LogModal"

type ReportLogModalProps = {
    logs: ReportLog[]
} & Omit<CommonLogModalProps, "title" | "emptyMessage">

export default function ReportLogModal(props: ReportLogModalProps) {
    const rows = useMemo(
        () =>
            props.logs.map(log => ({
                cells: [
                    { title: <LogLevelIcon level={log.level} /> },
                    { title: formatDateTime(log.timestamp * 1000) },
                    { title: <div dangerouslySetInnerHTML={{ __html: log.message }}></div> },
                ],
            })),

        [props.logs]
    )
    return (
        <LogModal
            title="Report logs"
            emptyMessage="There are no logs"
            {...props}
            columns={["Level", "Timestamp", "Message"]}
            fetchCount={() => Promise.resolve(props.logs.length)}
            fetchLogs={(page, limit) => Promise.resolve(rows.slice(page * limit, (page + 1) * limit))}
        />
    )
}
