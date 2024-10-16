import React, { CSSProperties, ReactElement, useEffect, useMemo, useState } from "react"
import { useSelector } from "react-redux"
import { teamsSelector } from "../../auth"

import {
    ToolbarGroup, ToolbarItem, ToolbarToggleGroup
} from '@patternfly/react-core';
import {
	Select,
	SelectOption,
	SelectOptionObject
} from '@patternfly/react-core/deprecated';

import { deepEquals, noop } from "../../utils"
import FilterIcon from "@patternfly/react-icons/dist/esm/icons/filter-icon";
import ToolbarLabelFilter from "../ToolbarLabelFilter";
import FilterDropDown from "./FilterDropDown";

export function convertLabels(obj: any): string {
    if (!obj) {
        return ""
    } else if (Object.keys(obj).length === 0) {
        return "<no labels>"
    }
    let str = ""
    for (const [key, value] of Object.entries(obj)) {
        if (str !== "") {
            str = str + ";"
        }
        str = str + key + ":" + convertLabelValue(value)
    }
    return str
}

function convertLabelValue(value: any) {
    if (typeof value === "object") {
        // Use the same format as postgres
        return JSON.stringify(value).replaceAll(",", ", ").replaceAll(":", ": ")
    }
    return value
}

function convertPartial(value: any) {
    if (typeof value === "object" && value !== null) {
        const copy = Array.isArray(value) ? [...value] : { ...value }
        copy.toString = () => convertLabelValue(value)
        return copy
      } 
        return value
}

export type SelectedLabels = SelectOptionObject | null

type LabelsSelectProps = {
    disabled?: boolean
    selection?: SelectedLabels
    onSelect(selection: SelectedLabels | undefined): void
    source(): Promise<any[]>
    emptyPlaceholder?: ReactElement | null
    style?: CSSProperties
    clearCallback( callback: () => void): any;
}

export default function LabelFilter({selection, onSelect, source, emptyPlaceholder, clearCallback}: LabelsSelectProps) {

    const [availableLabels, setAvailableLabels] = useState<any[]>([])

    const initialSelect = selection
        ? Object.entries(selection).reduce((acc, [key, value]) => {
              if (key !== "toString") {
                  acc[key] = convertPartial(value)
              }
              return acc
          }, {} as Record<string, any>)
        : {}
    const [partialSelect, setPartialSelect] = useState<any>(initialSelect)

    const teams = useSelector(teamsSelector)
    useEffect(() => {
        source().then((response: any[]) => {
            setAvailableLabels(response)
        }, noop)
    }, [source, onSelect, teams])

    const options = useMemo(() => {
        const opts : any[] = []
        availableLabels.map(t => ({ ...t, toString: () => convertLabels(t) })).forEach(o => opts.push(o))
        return opts
    }, [availableLabels])

    function getFilteredOptions(filter: any) {
        return availableLabels.filter(ls => {
            for (const [key, value] of Object.entries(filter)) {
                if (Array.isArray(value)) {
                    if (!deepEquals(ls[key], value)) {
                        return false
                    }
                } else if (typeof value === "object") {
                    const copy: any = { ...value }
                    delete copy.toString
                    if (!deepEquals(ls[key], copy)) {
                        return false
                    }
                } else if (ls[key] !== value) {
                    return false
                }
            }
            return true
        })
    }
    const filteredOptions = useMemo(() => getFilteredOptions(partialSelect), [availableLabels, partialSelect])
    const allOptions = [...new Set(availableLabels.flatMap(ls => Object.keys(ls)))]

    // Set up attribute selector
    const [activeAttributeMenu, setActiveAttributeMenu] = React.useState("");

    useMemo(() =>
            setActiveAttributeMenu(allOptions[0] || "")
        , [availableLabels]);

    const attributeDropdown = <FilterDropDown
        options={allOptions}
        activeAttributeMenu={activeAttributeMenu}
        setActiveAttributeMenu={setActiveAttributeMenu}
    />;

    const empty = !options || options.length === 0
    if (empty) {
        return emptyPlaceholder || null
    } else {
        const items = [...new Set(availableLabels.flatMap(ls => Object.keys(ls)))].map(key => {
            const values = filteredOptions.map(fo => fo[key])
            // javascript Set cannot use deep equality comparison
            const opts = values
                .filter((value, index) => {
                    for (let i = index + 1; i < values.length; ++i) {
                        if (deepEquals(value, values[i])) {
                            return false
                        }
                    }
                    return true
                })
                .map(value => convertPartial(value))
                .sort()

            return (

                    <ToolbarLabelFilter
                            key={key}
                            name={key}
                            options={opts}
                            filter={selection}
                            setFilter={onSelect}
                            activeMenu={activeAttributeMenu}
                            clearCallback={clearCallback}
                        />
                    )
        })

        return <ToolbarToggleGroup toggleIcon={<FilterIcon />} breakpoint="xl"  spacer={{ default: 'spacerLg' }}>
            <ToolbarGroup variant="filter-group">
                <ToolbarItem>{attributeDropdown}</ToolbarItem>
                {items}
            </ToolbarGroup>
        </ToolbarToggleGroup>

    }
}

