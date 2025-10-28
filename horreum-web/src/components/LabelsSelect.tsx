import {CSSProperties, ReactElement, useContext, useEffect, useMemo, useState} from "react"

import {
	Button,
	HelperText,
	Split,
	SplitItem
} from '@patternfly/react-core';

import { deepEquals, noop } from "../utils"
import { SimpleSelect } from "./templates/SimpleSelect";
import { TypeaheadSelect } from "./templates/TypeahedSelect"

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
    if (typeof value === "object") {
        const copy = Array.isArray(value) ? [...value] : { ...value }
        copy.toString = () => convertLabelValue(value)
        return copy
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
    emptyPlaceholder?: ReactElement<any> | null
    optionForAll?: string
    fireOnPartial?: boolean
    showKeyHelper?: boolean
    addResetButton?: boolean
    style?: CSSProperties
}

export default function LabelsSelect({disabled, selection, onSelect, source, emptyPlaceholder, fireOnPartial, showKeyHelper, addResetButton, style}: LabelsSelectProps) {
    const { teams } = useContext(AuthBridgeContext) as AuthContextType;
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

    useEffect(() => {
        source().then((response: any[]) => {
            setAvailableLabels(response)
            if (response && response.length === 1) {
                onSelect({ ...response[0], toString: () => convertLabels(response[0]) })
            }
        }, noop)
    }, [source, onSelect, teams])
    const options = useMemo(() => {
        const opts: SelectedLabels[] = []
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
    useEffect(() => {
        if (!fireOnPartial && filteredOptions.length === 1) {
            const str = convertLabels(filteredOptions[0])
            onSelect({ ...filteredOptions[0], toString: () => str })
        }
    }, [filteredOptions])

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
                <SplitItem key={key}>
                    {showKeyHelper && <HelperText>{key}:</HelperText>}
                    <InnerSelect
                        disabled={!!disabled}
                        hasOnlyOneOption={opts.length === 1 && partialSelect[key] === undefined}
                        selection={opts.length === 1 && opts[0] || partialSelect[key]}
                        options={opts}
                        onSelect={value => {
                            const partial = { ...partialSelect }
                            if (value !== undefined) {
                                partial[key] = value
                            } else {
                                delete partial[key]
                            }
                            setPartialSelect(partial)
                            if (fireOnPartial) {
                                const fo = getFilteredOptions(partial)
                                if (fo.length === 1) {
                                    onSelect(fo[0])
                                 } 
                                    onSelect(partial)
                                
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
        if (addResetButton) {
            items.push(
                <SplitItem style={{ alignSelf: "end" }} key="__reset_button">
                    <Button
                        onClick={() => {
                            setPartialSelect({})
                            onSelect({toString: () => ""})
                        }}
                    >
                        Reset
                    </Button>
                </SplitItem>
            )
        }
        return <Split style={style}>{items}</Split>
    }
}

type InnerSelectProps = {
    disabled: boolean
    hasOnlyOneOption?: boolean
    selection?: SelectedLabels
    options: SelectedLabels[]
    onSelect(opt: string | undefined): void
    onOpen?(): void
    placeholderText: string
    style?: CSSProperties
}

function InnerSelect({disabled, hasOnlyOneOption, selection, options, onSelect, onOpen, placeholderText, style}: InnerSelectProps) {
    const initialOptions = options.map(
        o => ({value: o?.toString(), content: o?.toString(), selected: o?.toString() === selection?.toString()})
    )
    return (
        <TypeaheadSelect
            initialOptions={initialOptions}
            onSelect={(_, item) => onSelect(item as string)}
            selected={selection?.toString()}
            onToggle={(expanding: any) => expanding && onOpen && onOpen()}
            onClearSelection={hasOnlyOneOption ? undefined : () => onSelect(undefined)}
            placeholder={placeholderText}
            isDisabled={disabled}
            style={style}
        />
    )
}
