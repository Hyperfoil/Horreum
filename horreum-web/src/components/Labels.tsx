import React, {useContext, useEffect, useMemo, useState} from "react"

import {NavLink} from "react-router-dom"

import {Checkbox, HelperText, HelperTextItem, Label, Split, SplitItem, Tooltip} from '@patternfly/react-core';
import {ExclamationCircleIcon} from "@patternfly/react-icons"

import {LabelInfo, schemaApi} from "../api"

import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";
import {MultiTypeaheadSelect, SimpleDropdown, TypeaheadSelect} from "@patternfly/react-templates";
import FilterIcon from "@patternfly/react-icons/dist/esm/icons/filter-icon";

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
    const [options, setOptions] = useState<LabelInfo[]>([])
    const [schemaFilter, setSchemaFilter] = useState(ALL_SCHEMAS)
    const [schemaFilterOptions, setSchemaFilterOptions] = useState<Record<string, string>>({__all__: "All schemas",})
    const [metrics, setMetrics] = useState(defaultMetrics === undefined || defaultMetrics)
    const [filtering, setFiltering] = useState(defaultFiltering === undefined || defaultFiltering)
    useEffect(() => {
        schemaApi.allLabels().then(
            labels => {
                setOptions(labels.sort())
                const sfo: Record<string, string> = {...schemaFilterOptions}
                labels.flatMap(l => l.schemas).forEach(s => (sfo[s.uri] = `${s.name === s.uri ? "" : s.name} [${s.uri}]`))
                setSchemaFilterOptions(sfo)
            },
            error => alerting.dispatchError(error, "LIST_ALL_LABELS", "Failed to list available labels.")
        )
    }, [])
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
        return {...o, toString: () => o.name}
    })
    const filteredOptions = useMemo(
        () => (schemaFilter === ALL_SCHEMAS
                ? options
                : options.filter(o => labels.includes(o.name) || (o.schemas.some(s => s.uri === schemaFilter) && ((o.metrics && metrics) || (o.filtering && filtering))))
        ).map(o => ({value: o.name, content: o.name, selected: labels.includes(o.name)})),
        [options, schemaFilter, metrics, filtering, labels]
    )
    return (
        <>
            {isReadOnly ||
                <Split>
                    <SplitItem>
                        <TypeaheadSelect
                            initialOptions={Object.entries(schemaFilterOptions).map(
                                ([name, title]) => ({value: name, content: title})
                            )}
                            selected={schemaFilter}
                            onSelect={(_, item) => setSchemaFilter(item as string)}
                            noOptionsFoundMessage={(filter) => `"${filter}" does not match any schema`}
                            isScrollable
                            toggleProps={{icon: <FilterIcon />, style: { alignItems: "center", paddingLeft: "1em" } }}
                        />
                    </SplitItem>
                    {schemaFilter === ALL_SCHEMAS || <SplitItem>
                        <SimpleDropdown
                            toggleContent={<FilterIcon/>}
                            initialItems={[
                                {
                                    value: "0",
                                    content:
                                        <Checkbox
                                            id="metrics"
                                            label="Include metrics labels"
                                            isChecked={metrics}
                                            onChange={(_, checked) => setMetrics(checked)}
                                        />
                                },
                                {
                                    value: "1",
                                    content:
                                        <Checkbox
                                            id="filtering"
                                            label="Include filtering labels"
                                            isChecked={filtering}
                                            onChange={(_, checked) => setFiltering(checked)}
                                        />
                                }
                            ]}
                        />
                    </SplitItem>}
                    <SplitItem isFilled>
                        <MultiTypeaheadSelect
                            initialOptions={filteredOptions}
                            placeholder={"Select label"}
                            noOptionsFoundMessage={(filter) => `"${filter}" label not found`}
                            onSelectionChange={(_, item) => onChange(item as string[])}
                            isScrollable
                            toggleProps={{status: error ? "danger" : undefined}}
                        />
                    </SplitItem>
                </Split>
            }
            {error && <HelperText><HelperTextItem variant="error">{error}</HelperTextItem></HelperText>}
            {selected.length == 0
                ? <>No labels</>
                : selected.map(o => (
                <div key={o.name} style={{marginTop: "0px"}}>
                    <Label color="grey">{o.name}</Label>
                    is valid for schemas:{"\u00A0"}
                    {o.schemas.length === 0 && (
                        <Tooltip content="No schemas implement this label!">
                            <ExclamationCircleIcon color="var(--pf-t--global--icon--color--status--danger--default)"/>
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
