import React, { useState, useEffect, useMemo } from "react"

import { Select, SelectOption, SelectOptionObject } from "@patternfly/react-core"

type TimeRangeSelectProps = {
    selection?: TimeRange
    onSelect(range: TimeRange): void
}

export type TimeRange = {
    from?: number
    to?: number
} & SelectOptionObject

function TimeRangeSelect(props: TimeRangeSelectProps) {
    const [isOpen, setOpen] = useState(false)
    const options: TimeRange[] = useMemo(
        () => [
            { toString: () => "all" },
            { from: Date.now() - 31 * 86_400_000, to: undefined, toString: () => "last month" },
            { from: Date.now() - 7 * 86_400_000, to: undefined, toString: () => "last week" },
            { from: Date.now() - 86_400_000, to: undefined, toString: () => "last 24 hours" },
        ],
        []
    )
    const selection = props.selection
    const onSelect = props.onSelect
    useEffect(() => {
        if (!selection) {
            onSelect(options[0])
        }
    }, [options, selection, onSelect])
    return (
        <Select
            onToggle={setOpen}
            onSelect={(e, selection) => {
                setOpen(false)
                props.onSelect(selection as TimeRange)
            }}
            selections={props.selection || options[0]}
            isOpen={isOpen}
            menuAppendTo="parent"
        >
            {options.map((tr, i) => (
                <SelectOption key={i} value={tr} />
            ))}
        </Select>
    )
}

export default TimeRangeSelect
