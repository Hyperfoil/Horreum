import { useState, useEffect } from "react"

import { Select, SelectOption, SelectOptionObject } from "@patternfly/react-core"

import {schemaApi} from "../api"

type SchemaSelectProps = {
    value?: string
    onChange(schema: string | undefined, id: number | undefined): void
    disabled?: string[]
    noSchemaOption?: boolean
    isCreatable?: boolean
}

interface Schema extends SelectOptionObject {
    name: string
    id: number
    uri: string
}

export default function SchemaSelect({value, onChange, disabled, noSchemaOption, isCreatable}: SchemaSelectProps) {
    const [isExpanded, setExpanded] = useState(false)
    const [options, setOptions] = useState<Schema[]>([])
    const noSchemaAllowed = noSchemaOption || false
    useEffect(() => {
        if (options.length > 0) {
            return
        }
        schemaApi.descriptors().then(response => {
            const schemas = response.map(s => {
                return { ...s, toString: () => `${s.name} (${s.uri})` }
            })
            setOptions(schemas)
            if (!noSchemaAllowed && !value && schemas.length > 0) {
                onChange(schemas[0].uri, schemas[0].id)
            }
        })
    }, [onChange, value, noSchemaAllowed])
    const extraOptions: Schema[] = []
    if (noSchemaAllowed) {
        extraOptions.push({ name: "", id: 0, uri: "", toString: () => "-- no schema --" })
    }
    return (
        <Select
            aria-label="Select schema"
            menuAppendTo="parent"
            maxHeight={300} // fixme: menu is clipped in modal
            isOpen={isExpanded}
            placeholderText="-- no schema --"
            variant="typeahead"
            isCreatable={isCreatable}
            createText="Use new schema URI: "
            onToggle={setExpanded}
            selections={options.find(o => o.uri === value) || []}
            onClear={() => {
                setExpanded(false)
                onChange(undefined, undefined)
            }}
            onSelect={(e, newValue) => {
                setExpanded(false)
                if (typeof newValue === "string") {
                    onChange(newValue as string, 0)
                } else {
                    const schema = newValue as Schema
                    onChange(schema.uri, schema.id)
                }
            }}
            onCreateOption={value => {
                setOptions([{ name: value, id: 0, uri: value, toString: () => value }, ...options])
                // onSelect runs automatically
            }}
        >
            {[...extraOptions, ...options].map((option, index) => (
                <SelectOption key={index} value={option} isDisabled={disabled?.includes(option.uri)}>
                    {option.name ? (
                            option.name === option.uri ?
                                (<code>{option.uri}</code>) :
                                (<>{option.name} (<code>{option.uri}</code>)</>)
                        )
                        : (option.toString())
                    }
                </SelectOption>
            ))}
        </Select>
    )
}
