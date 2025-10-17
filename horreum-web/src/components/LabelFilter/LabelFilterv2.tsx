import React, { CSSProperties, ReactElement, useEffect, useMemo, useState } from "react"
import { useSelector } from "react-redux"
import { teamsSelector } from "../../auth"

import {
    FlexItem,
    Flex,
    ToolbarGroup,
    ToolbarItem,
    ToolbarToggleGroup
} from '@patternfly/react-core';
import { BanIcon } from "@patternfly/react-icons"
import { deepEquals, noop } from "../../utils"
import FilterIcon from "@patternfly/react-icons/dist/esm/icons/filter-icon";
import ToolbarLabelFilter from "../ToolbarLabelFilter";
import FilterDropDown from "./FilterDropDown";

function convertLabelValue(value: any) {
    if (typeof value === "object") {
        // Use the same format as postgres
        return JSON.stringify(value).replaceAll(",", ", ").replaceAll(":", ": ")
    }
    return value
}

export type SelectedLabels = {
    toString(): string
}

type LabelsSelectProps = {
    disabled?: boolean
    selection?: SelectedLabels
    onSelect(selection: SelectedLabels | undefined): void
    source(): Promise<any[]>
    style?: CSSProperties
    clearCallback( callback: () => void): any;
}

export default function LabelFilterV2({selection, onSelect, source, clearCallback}: LabelsSelectProps) {
    const teams = useSelector(teamsSelector)
    const [availableLabels, setAvailableLabels] = useState<object>({})

    useEffect(() => {
        source().then((response: any[]) => {
            setAvailableLabels(response)
        }, noop)
    }, [source, onSelect, teams])

    const [activeAttributeMenu, setActiveAttributeMenu] = React.useState("");

    useMemo(() => setActiveAttributeMenu(Object.keys(availableLabels)[0] || ""), [availableLabels]);

    const attributeDropdown = <FilterDropDown
        options={Object.keys(availableLabels)}
        activeAttributeMenu={activeAttributeMenu}
        setActiveAttributeMenu={setActiveAttributeMenu}
    />;

    const empty = !availableLabels || Object.keys(availableLabels).length === 0
    if (empty) {
        return (
            <ToolbarItem>
                <Flex>
                    <FlexItem><BanIcon/></FlexItem>
                    <FlexItem>{"No filters available"}</FlexItem>
                </Flex>
            </ToolbarItem>
        )
    } else {
        const items = Object.entries(availableLabels).map(label => {
            const opts = label[1]
                .map(value => convertLabelValue(value))
                .sort()

            return (
                <ToolbarLabelFilter
                    key={label[0]}
                    name={label[0]}
                    options={opts}
                    filter={selection}
                    setFilter={onSelect}
                    activeMenu={activeAttributeMenu}
                    clearCallback={clearCallback}
                />
            )
        })

        return <ToolbarToggleGroup toggleIcon={<FilterIcon />} breakpoint="xl">
            <ToolbarGroup variant="filter-group">
                <ToolbarItem>{attributeDropdown}</ToolbarItem>
                {items}
            </ToolbarGroup>
        </ToolbarToggleGroup>
    }
}
