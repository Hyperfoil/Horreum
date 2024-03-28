import { useState} from "react"

import {
    Dropdown,
    DropdownItem, MenuToggle, MenuToggleElement,
} from "@patternfly/react-core"

import * as React from "react";

type FoldersDropDownProps = {
    folder: string
    folders : string []
    onChange(folder: string): void
}

export default function FoldersDropDown(props: FoldersDropDownProps) {
    const [isOpen, setIsOpen] = useState(false);

    const onToggle = () => {
        setIsOpen(!isOpen);
    };

    const onSelect = () => {
        setIsOpen(false);
    };
    const dropdownItems: any[] = []
    dropdownItems.push(<DropdownItem key="action" component="button" onClick={() => props.onChange('')}>
        Horreum
    </DropdownItem>)

    for (const folder of props.folders) {
        if (!folder) continue
        dropdownItems.push(<DropdownItem key="action" component="button" onClick={() => props.onChange(folder)}>
            {folder}
        </DropdownItem>)
    }
    return (
        <Dropdown
            style={{marginRight: "16px"}}
            onSelect={onSelect}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle ref={toggleRef} onClick={onToggle} isExpanded={isOpen}>
                    Folder
                </MenuToggle>
            )}
            isOpen={isOpen}
        >
            {dropdownItems}
        </Dropdown>
    )
}
