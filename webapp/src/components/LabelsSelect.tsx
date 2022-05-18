import { useEffect, useMemo, useState } from "react"
import { useSelector } from "react-redux"
import { teamsSelector } from "../auth"

import { Select, SelectOption, SelectOptionObject, Split, SplitItem } from "@patternfly/react-core"

import { useDispatch } from "react-redux"
import { deepEquals, noop } from "../utils"

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
        // Use the same format as Postgres
        return JSON.stringify(value).replaceAll(",", ", ").replaceAll(":", ": ")
    }
    return value
}

function convertPartial(value: any) {
    if (typeof value === "object") {
        return { ...value, toString: () => convertLabelValue(value) }
    } else {
        return value
    }
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
    const [availableLabels, setAvailableLabels] = useState<any[]>([])
    const [partialSelect, setPartialSelect] = useState<any>({})

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
    const filteredOptions = useMemo(() => {
        return availableLabels.filter(ls => {
            for (const [key, value] of Object.entries(partialSelect)) {
                if (typeof value === "object") {
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
    }, [availableLabels, partialSelect])
    useEffect(() => {
        if (filteredOptions.length === 1) {
            props.onSelect(filteredOptions[0])
        }
    }, [filteredOptions])

    const empty = !options || options.length === 0
    if (empty && !props.showIfNoLabels) {
        return <></>
    } else if (availableLabels.length < 16) {
        return (
            <InnerSelect
                disabled={props.disabled || empty}
                all={all}
                selection={props.selection === null ? all : props.selection}
                options={options}
                onSelect={props.onSelect}
                placeholderText="Choose labels..."
            />
        )
    } else {
        return (
            <Split>
                {[...new Set(availableLabels.flatMap(ls => Object.keys(ls)))].map(key => {
                    const opts = [...new Set(filteredOptions.map(fo => convertPartial(fo[key])))].sort()
                    return (
                        <SplitItem key={key}>
                            <InnerSelect
                                disabled={!!props.disabled}
                                isTypeahead
                                hasOnlyOneOption={opts.length === 1 && partialSelect[key] === undefined}
                                selection={opts.length === 1 ? opts[0] : partialSelect[key]}
                                options={opts}
                                onSelect={value => {
                                    const partial = { ...partialSelect }
                                    if (value !== undefined) {
                                        partial[key] = value
                                    } else {
                                        delete partial[key]
                                    }
                                    setPartialSelect(partial)
                                }}
                                placeholderText={`Choose ${key}...`}
                            />
                        </SplitItem>
                    )
                })}
            </Split>
        )
    }
}

type InnerSelectProps = {
    disabled: boolean
    isTypeahead?: boolean
    hasOnlyOneOption?: boolean
    selection?: SelectedLabels
    options: SelectOptionObject[]
    all?: SelectOptionObject
    onSelect(opt: SelectedLabels | undefined): void
    placeholderText: string
}

function InnerSelect(props: InnerSelectProps) {
    const [open, setOpen] = useState(false)
    return (
        <Select
            isDisabled={props.disabled || props.hasOnlyOneOption}
            variant={props.isTypeahead ? "typeahead" : "single"}
            isOpen={open}
            onToggle={setOpen}
            selections={props.selection === null ? props.all : props.selection}
            onSelect={(_, item) => {
                props.onSelect(item === props.all ? null : item)
                setOpen(false)
            }}
            onClear={props.hasOnlyOneOption ? undefined : () => props.onSelect(undefined)}
            menuAppendTo="parent"
            placeholderText={props.placeholderText}
        >
            {props.options.map((labels: SelectOptionObject | string, i: number) => (
                <SelectOption key={i} value={labels} />
            ))}
        </Select>
    )
}
