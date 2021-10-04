import React, { useState, useEffect } from "react"

import { all as allSchemas } from "../domain/schemas/api"

import { Select, SelectOption, SelectOptionObject } from "@patternfly/react-core"

type SchemaSelectProps = {
    value?: string
    onChange(schema: string | undefined, id: number | undefined): void
    disabled?: string[]
    noSchemaOption?: boolean
}

interface Schema extends SelectOptionObject {
    name: string
    id: number
    uri: string
}

/* This is going to be a complex component with modal for Extractor definition */
export default function SchemaSelect(props: SchemaSelectProps) {
    const [isExpanded, setExpanded] = useState(false)
    const [options, setOptions] = useState<Schema[]>([])
    useEffect(() => {
        // TODO: this is fetching all schemas including the schema JSONs
        allSchemas().then((response: Schema[]) => {
            const schemas = response.map(s => {
                return { name: s.name, id: s.id, uri: s.uri, toString: () => `${s.name} (${s.uri})` }
            })
            setOptions(schemas)
            if (!props.noSchemaOption && !props.value && schemas.length > 0) {
                props.onChange(schemas[0].uri, schemas[0].id)
            }
        })
    }, [props.onChange, props.value])
    var extraOptions: Schema[] = []
    if (props.noSchemaOption) {
        extraOptions.push({ name: "", id: 0, uri: "", toString: () => "-- no schema --" })
    }
    return (
        <Select
            aria-label="Select schema"
            isOpen={isExpanded}
            onToggle={setExpanded}
            selections={options.find(o => o.uri === props.value) || []}
            onClear={() => {
                setExpanded(false)
                props.onChange(undefined, undefined)
            }}
            onSelect={(e, newValue) => {
                const schema = newValue as Schema
                setExpanded(false)
                props.onChange(schema.uri, schema.id)
            }}
        >
            {[...extraOptions, ...options].map((option, index) => (
                <SelectOption key={index} value={option} isDisabled={props.disabled?.includes(option.uri)} />
            ))}
        </Select>
    )
}
