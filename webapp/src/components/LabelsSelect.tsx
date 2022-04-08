import { useEffect, useMemo, useState } from "react"
import { useSelector } from "react-redux"
import { teamsSelector } from "../auth"

import { Select, SelectOption, SelectOptionObject } from "@patternfly/react-core"

import { useDispatch } from "react-redux"
import { noop } from "../utils"

export function convertLabels(obj: any): string {
    if (!obj) {
        return ""
    } else if (Object.keys(obj).length === 0) {
        return "<no labels>"
    }
    let str = ""
    for (let [key, value] of Object.entries(obj)) {
        if (str !== "") {
            str = str + ";"
        }
        if (typeof value === "object") {
            // Use the same format as Postgres
            value = JSON.stringify(value).replaceAll(",", ", ").replaceAll(":", ": ")
        }
        str = str + key + ":" + value
    }
    return str
}

export type SelectedLabels = SelectOptionObject | null

type LabelsSelectProps = {
    disabled?: boolean
    selection?: SelectedLabels
    onSelect(selection: SelectedLabels): void
    source(): Promise<any[]>
    showIfNoLabels?: boolean
    optionForAll?: string
}

export default function LabelsSelect(props: LabelsSelectProps) {
    const [open, setOpen] = useState(false)
    const [availableLabels, setAvailableLabels] = useState<any[]>([])

    const dispatch = useDispatch()
    const onSelect = props.onSelect
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        props.source().then((response: any[]) => {
            setAvailableLabels(response)
            if (!props.optionForAll && response && response.length === 1) {
                onSelect({ ...response[0], toString: () => convertLabels(response[0]) })
            }
        }, noop)
    }, [props.source, onSelect, dispatch, teams, props.optionForAll])
    const all: SelectOptionObject = useMemo(
        () => ({
            toString: () => props.optionForAll || "",
        }),
        [props.optionForAll]
    )
    const options = useMemo(() => {
        const opts = []
        if (props.optionForAll) {
            opts.push(all)
        }
        availableLabels.map(t => ({ ...t, toString: () => convertLabels(t) })).forEach(o => opts.push(o))
        return opts
    }, [availableLabels, all])

    const empty = !options || options.length === 0
    if (empty && !props.showIfNoLabels) {
        return <></>
    }
    // TODO: decompose into multiple selects if there are too many options
    return (
        <Select
            isDisabled={props.disabled || empty}
            isOpen={open}
            onToggle={setOpen}
            selections={props.selection === null ? all : props.selection}
            onSelect={(_, item) => {
                props.onSelect(item === all ? null : item)
                setOpen(false)
            }}
            menuAppendTo="parent"
            placeholderText="Choose labels..."
        >
            {options.map((labels: SelectOptionObject | string, i: number) => (
                <SelectOption key={i} value={labels} />
            ))}
        </Select>
    )
}
