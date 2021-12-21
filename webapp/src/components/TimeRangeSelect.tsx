import { useState, useEffect } from "react"

import { Select, SelectOption, SelectOptionObject } from "@patternfly/react-core"

type TimeRangeSelectProps = {
    selection?: TimeRange
    onSelect(range: TimeRange): void
    options: TimeRange[]
}

export type TimeRange = {
    from?: number
    to?: number
} & SelectOptionObject

function TimeRangeSelect(props: TimeRangeSelectProps) {
    const [isOpen, setOpen] = useState(false)
    const selection = props.selection
    const onSelect = props.onSelect
    useEffect(() => {
        if (!selection) {
            onSelect(props.options[0])
        }
    }, [props.options, selection, onSelect])
    return (
        <Select
            onToggle={setOpen}
            onSelect={(e, selection) => {
                setOpen(false)
                props.onSelect(selection as TimeRange)
            }}
            selections={props.selection || props.options[0]}
            isOpen={isOpen}
            menuAppendTo="parent"
        >
            {props.options.map((tr, i) => (
                <SelectOption key={i} value={tr} />
            ))}
        </Select>
    )
}

export default TimeRangeSelect
