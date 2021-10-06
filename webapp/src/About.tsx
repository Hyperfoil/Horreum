import { useState, useEffect } from "react"
import { Bullseye, Button, Modal, Spinner } from "@patternfly/react-core"
import { QuestionCircleIcon } from "@patternfly/react-icons"
import { Table, TableBody } from "@patternfly/react-table"

import { fetchApi } from "./services/api/index"
import { formatDateTime } from "./utils"

type VersionInfo = {
    version: string
    commit: string
    buildTimestamp: number
    startTimestamp: number
}

const VERSION_ERROR = {
    version: "<unknown:error>",
    commit: "<unknown:error>",
    buildTimestamp: 0,
    startTimestamp: 0,
}

export default function About() {
    const [isOpen, setOpen] = useState(false)
    const [versionInfo, setVersionInfo] = useState<VersionInfo>()
    useEffect(() => {
        if (isOpen) {
            fetchApi("/api/config/version").then(
                response => setVersionInfo(response),
                _ => setVersionInfo(VERSION_ERROR)
            )
        }
    }, [isOpen])
    return (
        <>
            <Button variant="plain" style={{ marginLeft: "16px" }} onClick={() => setOpen(true)}>
                <QuestionCircleIcon style={{ fill: "#ffffff" }} />
            </Button>
            <Modal
                variant="small"
                title="About Horreum"
                isOpen={isOpen}
                onClose={() => setOpen(false)}
                actions={[
                    <Button key={0} onClick={() => setOpen(false)}>
                        Close
                    </Button>,
                ]}
            >
                {!versionInfo && (
                    <Bullseye>
                        <Spinner size="xl" />
                    </Bullseye>
                )}
                {versionInfo && (
                    <Table
                        aria-label="version info"
                        variant="compact"
                        cells={["Category", "Value"]}
                        rows={[
                            ["Version", versionInfo.version],
                            ["Git commit ID", versionInfo.commit],
                            ["Build timestamp", formatDateTime(versionInfo.buildTimestamp)],
                            ["Start timestamp", formatDateTime(versionInfo.startTimestamp)],
                        ]}
                    >
                        <TableBody />
                    </Table>
                )}
            </Modal>
        </>
    )
}
