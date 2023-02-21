import { useState, useEffect } from "react"
import { Bullseye, Button, Dropdown, DropdownItem, DropdownToggle, Modal, Spinner } from "@patternfly/react-core"
import { QuestionCircleIcon } from "@patternfly/react-icons"
import { Table, TableBody } from "@patternfly/react-table"

import Api from "./api"
import { formatDateTime } from "./utils"

type VersionInfo = {
    version: string
    startTimestamp: number
}

const VERSION_ERROR = {
    version: "<unknown:error>",
    startTimestamp: 0,
}

export default function About() {
    const [isDropdownOpen, setDropdownOpen] = useState(false)
    const [isModalOpen, setModalOpen] = useState(false)
    const [versionInfo, setVersionInfo] = useState<VersionInfo>()
    useEffect(() => {
        if (isModalOpen) {
            Api.configServiceVersion().then(
                response => setVersionInfo(response),
                _ => setVersionInfo(VERSION_ERROR)
            )
        }
    }, [isModalOpen])
    return (
        <>
            <Dropdown
                style={{ marginLeft: "16px" }}
                position="right"
                menuAppendTo="parent"
                onSelect={() => setDropdownOpen(false)}
                toggle={
                    <DropdownToggle toggleIndicator={null} onToggle={setDropdownOpen} id="toggle-icon-only">
                        <QuestionCircleIcon style={{ fill: "#ffffff" }} />
                    </DropdownToggle>
                }
                isOpen={isDropdownOpen}
                isPlain
                dropdownItems={[
                    <DropdownItem
                        key="docs"
                        onClick={() => {
                            const newTab = window.open("https://horreum.hyperfoil.io", "_blank")
                            if (newTab) newTab.focus()
                        }}
                    >
                        Project documentation
                    </DropdownItem>,
                    <DropdownItem key="version info" onClick={() => setModalOpen(true)}>
                        Version info
                    </DropdownItem>,
                ]}
            />
            <Modal
                variant="small"
                title="About Horreum"
                isOpen={isModalOpen}
                onClose={() => setModalOpen(false)}
                actions={[
                    <Button key={0} onClick={() => setModalOpen(false)}>
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
                            ["Up since", formatDateTime(versionInfo.startTimestamp)],
                        ]}
                    >
                        <TableBody />
                    </Table>
                )}
            </Modal>
        </>
    )
}
