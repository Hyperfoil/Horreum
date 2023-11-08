import React, {ReactNode, RefObject, useContext, useEffect, useMemo, useRef, useState} from "react"

import { NavLink } from "react-router-dom"

import { Checkbox, Flex, FlexItem, Select, SelectOption, Tooltip } from "@patternfly/react-core"
import { ExclamationCircleIcon } from "@patternfly/react-icons"

import {LabelInfo, schemaApi} from "../api"

import EnumSelect from "./EnumSelect"
import NameUri from "./NameUri"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";

type LabelsProps = {
    labels: string[]
    onChange(labels: string[]): void
    isReadOnly: boolean
    error?: string
    defaultMetrics?: boolean
    defaultFiltering?: boolean
}

const ALL_SCHEMAS = "__all__"

export default function Labels({ labels, onChange, isReadOnly, error, defaultMetrics, defaultFiltering }: LabelsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [isExpanded, setExpanded] = useState(false)
    const [options, setOptions] = useState<LabelInfo[]>([])
    const [schemaFilter, setSchemaFilter] = useState(ALL_SCHEMAS)
    const [schemaFilterOptions, setSchemaFilterOptions] = useState<Record<string, ReactNode>>({
        __all__: "All schemas",
    })
    const [metrics, setMetrics] = useState(defaultMetrics === undefined || defaultMetrics)
    const [filtering, setFiltering] = useState(defaultFiltering === undefined || defaultFiltering)
    useEffect(() => {
        schemaApi.allLabels().then(
            labels => {
                setOptions(labels)
                const sfo: Record<string, ReactNode> = { ...schemaFilterOptions }
                labels.flatMap(l => l.schemas).forEach(s => (sfo[s.uri] = <NameUri descriptor={s} />))
                setSchemaFilterOptions(sfo)
            },
            error => alerting.dispatchError(error, "LIST_ALL_LABELS", "Failed to list available labels.")
        )
    }, [])
    useEffect(() => {
        if (!isExpanded) {
            setSchemaFilter(ALL_SCHEMAS)
        }
    }, [isExpanded])
    const selected = labels.map(l => {
        const o = options.find(l2 => l2.name === l)
        if (!o) {
            return {
                name: l,
                metrics: false,
                filtering: false,
                schemas: [],
                toString: () => l,
            }
        }
        return { ...o, toString: () => o.name }
    })
    const footerRef = useRef<HTMLDivElement>()
    function ensureFooterInView() {
        setTimeout(() => {
            if (footerRef.current) {
                const { bottom } = footerRef.current.getBoundingClientRect()
                if (bottom > (window.innerHeight || document.documentElement.clientHeight)) {
                    footerRef.current.scrollIntoView({ behavior: "smooth", block: "end" })
                }
            }
        }, 300)
    }
    const filteredOptions = useMemo(
        () =>
            options
                .filter(o => schemaFilter === "__all__" || o.schemas.some(s => s.uri === schemaFilter))
                .filter(o => !labels.includes(o.name) && ((o.metrics && metrics) || (o.filtering && filtering)))
                .sort()
                .map((o, index) => <SelectOption key={index} value={o.name} />),
        [options, schemaFilter, labels]
    )
    return (
        <>
            <Select
                variant="typeaheadmulti"
                aria-label="Select label(s)"
                validated={error ? "error" : "default"}
                placeholderText={isReadOnly ? "No labels" : "Select label(s)"}
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
                onFilter={(_, value) => filteredOptions.filter(o => (o.props.value as string).indexOf(value) >= 0)}
                onSelect={(_, newValue) => {
                    setExpanded(false)
                    const label = newValue.toString()
                    onChange(labels.includes(label) ? labels.filter(l => l !== label) : [...labels, label])
                }}
                footer={
                    <div ref={footerRef as RefObject<HTMLDivElement>}>
                        <Flex>
                            <FlexItem>Filter by schema:</FlexItem>
                            <FlexItem>
                                <EnumSelect
                                    options={schemaFilterOptions}
                                    selected={schemaFilter}
                                    onSelect={setSchemaFilter}
                                />
                            </FlexItem>
                        </Flex>
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
                {filteredOptions}
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
                    {o.schemas.length === 0 && (
                        <Tooltip content="No schemas implement this label!">
                            <ExclamationCircleIcon
                                style={{
                                    fill: "var(--pf-global--danger-color--100)",
                                    marginTop: "4px",
                                }}
                            />
                        </Tooltip>
                    )}
                    {o.schemas.map((d, i) => (
                        <React.Fragment key={i}>
                            <Tooltip maxWidth="80vw" content={<code>{d.uri}</code>}>
                                <NavLink to={`/schema/${d.id}#labels+${encodeURIComponent(o.name)}`}>{d.name}</NavLink>
                            </Tooltip>
                            {i != o.schemas.length - 1 && ",\u00A0"}
                        </React.Fragment>
                    ))}
                </div>
            ))}
        </>
    )
}
