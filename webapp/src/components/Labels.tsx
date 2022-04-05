import React, { useState, useEffect } from "react"
import { useDispatch } from "react-redux"

import { NavLink } from "react-router-dom"
import { listAllLabels, LabelInfo } from "../domain/schemas/api"

import { dispatchError } from "../alerts"

import { Select, SelectOption, Tooltip } from "@patternfly/react-core"

type LabelsProps = {
    labels: string[]
    onChange(labels: string[]): void
    isReadOnly: boolean
    error?: string
}

export default function Labels({ labels: value, onChange, isReadOnly, error }: LabelsProps) {
    const [isExpanded, setExpanded] = useState(false)
    const [options, setOptions] = useState<LabelInfo[]>([])
    const dispatch = useDispatch()
    useEffect(() => {
        listAllLabels().then(setOptions, error =>
            dispatchError(dispatch, error, "LIST_ALL_LABELS", "Failed to list available labels.")
        )
    }, [])
    const selected = value
        .map(l => options.find(l2 => l2.name === l))
        .filter(o => o !== undefined)
        .map(o => o as LabelInfo)
        .map(o => ({ ...o, toString: () => o.name }))
    return (
        <>
            <Select
                variant="typeaheadmulti"
                aria-label="Select label(s)"
                validated={error ? "error" : "default"}
                placeholderText="Select label(s)"
                isOpen={isExpanded}
                onToggle={setExpanded}
                selections={selected}
                isDisabled={isReadOnly}
                onClear={() => {
                    setExpanded(false)
                    onChange([])
                }}
                onSelect={(e, newValue) => {
                    setExpanded(false)
                    onChange([...value, newValue.toString()])
                }}
            >
                {[...options.filter(o => value.includes(o.name))].sort().map((o, index) => (
                    <SelectOption key={index} value={o.name} />
                ))}
            </Select>
            {error && (
                <span
                    style={{
                        display: "inline-block",
                        color: "var(--pf-global--danger-color--100)",
                    }}
                >
                    {error}
                </span>
            )}
            {selected.map(o => (
                <div key={o.name} style={{ marginTop: "5px" }}>
                    <span
                        style={{
                            border: "1px solid #888",
                            borderRadius: "4px",
                            padding: "4px",
                            backgroundColor: "#f0f0f0",
                        }}
                    >
                        {o.name}
                    </span>{" "}
                    is valid for schemas:{"\u00A0"}
                    {o.schemas.map((d, i) => (
                        <React.Fragment key={i}>
                            <Tooltip maxWidth="80vw" content={<code>{d.uri}</code>}>
                                <NavLink to={`/schema/${d.id}`}>{d.name}</NavLink>
                            </Tooltip>
                            {i != o.schemas.length - 1 && ",\u00A0"}
                        </React.Fragment>
                    ))}
                </div>
            ))}
        </>
    )
}
