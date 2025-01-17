import {useState, useEffect, Ref} from "react"
import {
    Bullseye,
    Button,
    DescriptionList,
    DescriptionListDescription,
    DescriptionListGroup,
    DescriptionListTerm,
    Dropdown,
    DropdownItem,
    DropdownList,
    MenuToggle,
    MenuToggleElement,
    Modal,
    Spinner
} from '@patternfly/react-core';
import { QuestionCircleIcon } from "@patternfly/react-icons"

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
                isOpen={isDropdownOpen}
                onSelect={() => setDropdownOpen(false)}
                popperProps={{preventOverflow: true, position: "end"}}
                toggle={(toggleRef: Ref<MenuToggleElement>) => (
                    <MenuToggle
                        id="toggle-icon-only"
                        ref={toggleRef}
                        variant="plain"
                        onClick={() => setDropdownOpen(!isDropdownOpen)}>
                        <QuestionCircleIcon color="white"/>
                    </MenuToggle>
                )}
            >
                <DropdownList>
                    <DropdownItem
                        key="docs"
                        onClick={() => {
                            const newTab = window.open("https://horreum.hyperfoil.io", "_blank")
                            if (newTab) newTab.focus()
                        }}
                    >
                        Project documentation
                    </DropdownItem>
                    <DropdownItem key="version info" onClick={() => setModalOpen(true)}>
                        Version info
                    </DropdownItem>
                </DropdownList>
            </Dropdown>
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
                    <DescriptionList isHorizontal aria-label="version-info">
                        <DescriptionListGroup>
                            <DescriptionListTerm>Version</DescriptionListTerm>
                            <DescriptionListDescription>{versionInfo.version}</DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                            <DescriptionListTerm>Up Since</DescriptionListTerm>
                            <DescriptionListDescription>{formatDateTime(versionInfo.startTimestamp)}</DescriptionListDescription>
                        </DescriptionListGroup>
                        {versionInfo.privacyStatement ?
                            <DescriptionListGroup>
                                <DescriptionListTerm>Privacy Statement</DescriptionListTerm>
                                <DescriptionListDescription>
                                    <a href={versionInfo.privacyStatement}>{versionInfo.privacyStatement}</a>
                                </DescriptionListDescription>
                            </DescriptionListGroup> :
                            <></>
                        }
                    </DescriptionList>
                )}
            </Modal>
        </>
    )
}
