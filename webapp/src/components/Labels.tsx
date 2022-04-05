import React, { RefObject, useRef, useState, useEffect } from "react"
import { useDispatch } from "react-redux"

import { NavLink } from "react-router-dom"
import { listAllLabels, LabelInfo } from "../domain/schemas/api"

import { dispatchError } from "../alerts"

import { Checkbox, Select, SelectOption, Tooltip } from "@patternfly/react-core"

type LabelsProps = {
    labels: string[]
    onChange(labels: string[]): void
    isReadOnly: boolean
    error?: string
    defaultMetrics?: boolean
    defaultFiltering?: boolean
}

export default function Labels({ labels, onChange, isReadOnly, error, defaultMetrics, defaultFiltering }: LabelsProps) {
    const [isExpanded, setExpanded] = useState(false)
    const [options, setOptions] = useState<LabelInfo[]>([])
    const [metrics, setMetrics] = useState(defaultMetrics === undefined || defaultMetrics)
    const [filtering, setFiltering] = useState(defaultFiltering === undefined || defaultFiltering)
    const dispatch = useDispatch()
    useEffect(() => {
        listAllLabels().then(setOptions, error =>
            dispatchError(dispatch, error, "LIST_ALL_LABELS", "Failed to list available labels.")
        )
    }, [])
    const selected = labels
        .map(l => options.find(l2 => l2.name === l))
        .filter(o => o !== undefined)
        .map(o => o as LabelInfo)
        .map(o => ({ ...o, toString: () => o.name }))
    const footerRef = useRef<HTMLDivElement>()
    function ensureFooterInView() {
        setTimeout(() => {
            console.log(footerRef)
            if (footerRef.current) {
                const { bottom } = footerRef.current.getBoundingClientRect()
                if (bottom > (window.innerHeight || document.documentElement.clientHeight)) {
                    footerRef.current.scrollIntoView({ behavior: "smooth", block: "end" })
                }
            }
        }, 300)
    }
    return (
        <>
            <Select
                variant="typeaheadmulti"
                aria-label="Select label(s)"
                validated={error ? "error" : "default"}
                placeholderText="Select label(s)"
                isOpen={isExpanded}
                maxHeight={"50vh"}
                onToggle={expanded => {
                    setExpanded(expanded)
                    if (expanded) {
                        ensureFooterInView()
                    }
                }}
                selections={selected}
                isDisabled={isReadOnly}
                onClear={() => {
                    setExpanded(false)
                    onChange([])
                }}
                onSelect={(e, newValue) => {
                    setExpanded(false)
                    const label = newValue.toString()
                    onChange(labels.includes(label) ? labels.filter(l => l !== label) : [...labels, label])
                }}
                footer={
                    <div ref={footerRef as RefObject<HTMLDivElement>}>
                        <Checkbox
                            id="metrics"
                            label="Include metrics labels"
                            isChecked={metrics}
                            onChange={checked => {
                                setMetrics(checked)
                                ensureFooterInView()
                            }}
                        />
                        <Checkbox
                            id="filtering"
                            label="Include filtering labels"
                            isChecked={filtering}
                            onChange={checked => {
                                setFiltering(checked)
                                ensureFooterInView()
                            }}
                        />
                    </div>
                }
            >
                {options
                    .filter(o => !labels.includes(o.name) && ((o.metrics && metrics) || (o.filtering && filtering)))
                    .sort()
                    .map((o, index) => (
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
