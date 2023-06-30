import { useState} from "react"

import {
    Badge,
    Dropdown,
    DropdownItem, DropdownList, Icon, MenuToggle, MenuToggleElement,
} from "@patternfly/react-core"

import * as React from "react";

import CaretDownIcon from '@patternfly/react-icons/dist/esm/icons/caret-down-icon';
import AngleRightIcon from '@patternfly/react-icons/dist/esm/icons/angle-right-icon';
import AngleLeftIcon from '@patternfly/react-icons/dist/esm/icons/angle-left-icon';

const ROOT_FOLDER = "Horreum"
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
    dropdownItems.push(
        <DropdownItem key={ROOT_FOLDER} onClick={() => props.onChange('')}>
            {ROOT_FOLDER}
        </DropdownItem>
    )

    for (const folder of props.folders) {
        if (!folder) continue
        dropdownItems.push(
            <DropdownItem key={folder} onClick={() => props.onChange(folder)}
                icon={
                    <Icon>
                        <AngleRightIcon/>
                    </Icon>
                }
            >
                {folder}
            </DropdownItem>
        )
    }
    return (
        <Dropdown
            style={{marginRight: "16px"}}
            onSelect={onSelect}
            onOpenChange={(isOpen: boolean) => setIsOpen(isOpen)}
            toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                <MenuToggle ref={toggleRef} onClick={onToggle} isExpanded={isOpen} variant="plain">
                    <Badge isRead screenReaderText="selectable folders">
                        Tests
                        <span>
                            <CaretDownIcon />
                        </span>
                    </Badge>
                </MenuToggle>
            )}
            isOpen={isOpen}
        >
            <DropdownList>
                {dropdownItems.map((dropdownItem) => dropdownItem)}
            </DropdownList>
        </Dropdown>
    )
}
