import { CSSProperties, ReactElement, useEffect, useMemo, useState } from "react"
import { useSelector } from "react-redux"
import { teamsSelector } from "../auth"

import { Button, HelperText, Select, SelectOption, SelectOptionObject, Split, SplitItem } from "@patternfly/react-core"

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
        const copy = Array.isArray(value) ? [...value] : { ...value }
        copy.toString = () => convertLabelValue(value)
        return copy
    } else {
        return value
    }
}

export type SelectedLabels = SelectOptionObject | null

type LabelsSelectProps = {
    disabled?: boolean
    selection?: SelectedLabels
    onSelect(selection: SelectedLabels | undefined): void
    source(): Promise<any[]>
    emptyPlaceholder?: ReactElement | null
    optionForAll?: string
    forceSplit?: boolean
    fireOnPartial?: boolean
    showKeyHelper?: boolean
    addResetButton?: boolean
    style?: CSSProperties
}

export default function LabelsSelect(props: LabelsSelectProps) {
    const [availableLabels, setAvailableLabels] = useState<any[]>([])
    const initialSelect = props.selection
        ? Object.entries(props.selection).reduce((acc, [key, value]) => {
              if (key !== "toString") {
                  acc[key] = convertPartial(value)
              }
              return acc
          }, {} as Record<string, any>)
        : {}
    const [partialSelect, setPartialSelect] = useState<any>(initialSelect)

    const dispatch = useDispatch()
    const teams = useSelector(teamsSelector)
    useEffect(() => {
        props.source().then((response: any[]) => {
            setAvailableLabels(response)
            if (!props.optionForAll && response && response.length === 1) {
                props.onSelect({ ...response[0], toString: () => convertLabels(response[0]) })
            }
        }, noop)
    }, [props.source, props.onSelect, dispatch, teams, props.optionForAll])
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
    useEffect(() => {
        if (!props.fireOnPartial && filteredOptions.length === 1) {
            const str = convertLabels(filteredOptions[0])
            props.onSelect({ ...filteredOptions[0], toString: () => str })
        }
    }, [filteredOptions])

    const empty = !options || options.length === 0
    if (empty) {
        return props.emptyPlaceholder || null
    } else if (!props.forceSplit && availableLabels.length < 16) {
        return (
            <InnerSelect
                disabled={props.disabled || empty}
                all={all}
                selection={props.selection === null ? all : props.selection}
                options={options}
                onSelect={props.onSelect}
                placeholderText="Choose labels..."
                style={props.style}
            />
        )
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
                <SplitItem key={key}>
                    {props.showKeyHelper && <HelperText>{key}:</HelperText>}
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
                            if (props.fireOnPartial) {
                                const fo = getFilteredOptions(partial)
                                if (fo.length === 1) {
                                    props.onSelect(fo[0])
                                } else {
                                    props.onSelect(partial)
                                }
                            }
                        }}
                        onOpen={() => {
                            const partial = { ...partialSelect }
                            delete partial[key]
                            setPartialSelect(partial)
                        }}
                        placeholderText={`Choose ${key}...`}
                    />
                </SplitItem>
            )
        })
        if (props.addResetButton) {
            items.push(
                <SplitItem style={{ alignSelf: "end" }} key="__reset_button">
                    <Button
                        onClick={() => {
                            setPartialSelect({})
                            props.onSelect({})
                        }}
                    >
                        Reset
                    </Button>
                </SplitItem>
            )
        }
        return <Split style={props.style}>{items}</Split>
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
    onOpen?(): void
    placeholderText: string
    style?: CSSProperties
}

function InnerSelect(props: InnerSelectProps) {
    const [open, setOpen] = useState(false)
    return (
        <Select
            isDisabled={props.disabled || props.hasOnlyOneOption}
            variant={props.isTypeahead ? "typeahead" : "single"}
            isOpen={open}
            onToggle={expanded => {
                if (expanded && props.onOpen) {
                    props.onOpen()
                }
                setOpen(expanded)
            }}
            selections={[props.selection === null ? props.all : props.selection]}
            onSelect={(_, item) => {
                props.onSelect(item === props.all ? null : item)
                setOpen(false)
            }}
            onClear={props.hasOnlyOneOption ? undefined : () => props.onSelect(undefined)}
            menuAppendTo="parent"
            placeholderText={props.placeholderText}
            style={props.style}
            width={props.style?.width || "auto"}
        >
            {props.options.map((labels: SelectOptionObject | string, i: number) => (
                <SelectOption key={i} value={labels} />
            ))}
        </Select>
    )
}
