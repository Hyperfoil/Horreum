import { useState, useEffect } from "react"
import {
	Bullseye,
	Button,
	MenuToggle,
	Modal,
	Spinner
} from '@patternfly/react-core';
import {
	Dropdown,
	DropdownItem,
	DropdownToggle
} from '@patternfly/react-core/deprecated';
import { QuestionCircleIcon } from "@patternfly/react-icons"
import {
	Table,
	TableBody,
} from '@patternfly/react-table/deprecated';
import {
    TableText
} from '@patternfly/react-table';

import MoonIcon from '@patternfly/react-icons/dist/esm/icons/moon-icon';
import SunIcon from '@patternfly/react-icons/dist/esm/icons/sun-icon';

import { configApi } from "./api"
import { formatDateTime } from "./utils"

type VersionInfo = {
    version: string
    startTimestamp: number
    privacyStatement?: string
}

const VERSION_ERROR = {
    version: "<unknown:error>",
    startTimestamp: 0,
}

export default function About() {
    const [isDarkTheme, setIsDarkTheme] = useState(JSON.parse(localStorage.getItem('dark-theme') ?? 'false') as boolean);
    const applyTheme = () => (document.querySelector('html') as Element).classList.toggle('pf-v5-theme-dark', isDarkTheme);

    useEffect(() => {
        localStorage.setItem('dark-theme', JSON.stringify(isDarkTheme));
        applyTheme();
    }, [isDarkTheme]);

    const [isDropdownOpen, setDropdownOpen] = useState(false)
    const [isModalOpen, setModalOpen] = useState(false)
    const [versionInfo, setVersionInfo] = useState<VersionInfo>()
    useEffect(() => {
        if (isModalOpen) {
            configApi.version().then(
                response => setVersionInfo(response),
                _ => setVersionInfo(VERSION_ERROR)
            )
        }
    }, [isModalOpen])
    return (
        <>
            <MenuToggle id="toggle-dark-theme" variant="plain" onClick={() => setIsDarkTheme(!isDarkTheme)}>
                { isDarkTheme ? <SunIcon style={{ color: '#FC0' }} /> : <MoonIcon style={{ color: '#FEC' }} /> }
            </MenuToggle>
            <Dropdown
                style={{ marginLeft: "16px" }}
                position="right"
                menuAppendTo="parent"
                onSelect={() => setDropdownOpen(false)}
                toggle={
                    <DropdownToggle toggleIndicator={null} onToggle={(_event, val) => setDropdownOpen(val)} id="toggle-icon-only">
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
                            versionInfo.privacyStatement
                                ? ["Privacy Statement",  <TableText><a href={versionInfo.privacyStatement}>{versionInfo.privacyStatement}</a></TableText>]
                                : [],
                        ]}
                    >
                        <TableBody />
                    </Table>
                )}
            </Modal>
        </>
    )
}
