import {useState, useEffect} from "react"

import {TypeaheadSelect} from "@patternfly/react-templates";

import {schemaApi} from "../api"

type SchemaSelectProps = {
    value?: string
    onChange(schema: string | undefined, id: number | undefined): void
    disabled?: string[]
    noSchemaOption?: boolean
    isCreatable?: boolean
}

interface Schema {
    name: string
    id: number
    uri: string
    toString: () => string
}

export default function SchemaSelect({value, onChange, disabled, noSchemaOption, isCreatable}: SchemaSelectProps) {
    const [options, setOptions] = useState<Schema[]>([])
    const noSchemaAllowed = noSchemaOption || false
    useEffect(() => {
        options.length || schemaApi.descriptors().then(response => {
            const schemas = response.map(s => ({...s, toString: () => `${s.name === s.uri ? "" : s.name} [${s.uri}]`}))
            setOptions(schemas)
            if (!noSchemaAllowed && !value && schemas.length > 0) {
                onChange(schemas[0].uri, schemas[0].id)
            }
        })
    }, [onChange, value, noSchemaAllowed])
    return (
        <TypeaheadSelect
            selectOptions={
                (noSchemaAllowed ? [{name: "", id: 0, uri: "", toString: () => "-- no schema --"}] : []).concat(options)
                    .map(o => ({value: o.id, content: o.toString(), disabled: disabled?.includes(o.uri)}))
            }
            selected={value !== undefined && value.length > 0 ? options.find(o => o.uri === value)?.id ?? 0 : 0}
            onClearSelection={value !== undefined && value.length > 0 ? () => onChange(undefined, undefined) : undefined}
            onSelect={(_, item) => {
                if (typeof item === "string") {
                    onChange(item as string, 0)
                } else {
                    onChange(options.find(o => o.id === item as number)?.uri, item as number)
                }
            }}
            isCreatable={isCreatable}
            createOptionMessage={(item) => `Use new schema URI: ${item}`}
            isScrollable
            toggleWidth="100%"
            maxMenuHeight="45vh"
            popperProps={{enableFlip: false, preventOverflow: true, maxWidth: "200px"}}
        />
    )
}
