import React, { useState, useEffect } from 'react'

import { all as allSchemas } from '../domain/schemas/api'

import {
   Select,
   SelectOption,
   SelectOptionObject,
} from '@patternfly/react-core';

type SchemaSelectProps = {
   value: string,
   onChange(schema: string | undefined): void,
   disabled: string[],
}

interface Schema extends SelectOptionObject {
   name: string,
   uri: string,
}

/* This is going to be a complex component with modal for Extractor definition */
export default ({ value = "", onChange = (_: string) => {}, disabled = []}: SchemaSelectProps) => {
   const [isExpanded, setExpanded] = useState(false)
   const [selected, setSelected] = useState<Schema | null>(null)
   const [options, setOptions] = useState<Schema[]>([])
   useEffect(() => {
      // TODO: this is fetching all schemas including the schema JSONs
      allSchemas().then((response: Schema[]) => {
         const schemas = response.map(s => { return { name: s.name, uri: s.uri }; })
         setOptions(schemas)
         if (selected === null && schemas.length > 0) {
            onChange(schemas[0].uri)
         }
      })
   }, [])
   useEffect(() => {
      if (value && value !== "") {
         const o = options.find(s => s.uri === value)
         if (o && o !== selected) {
            setSelected({ ...o, toString: () => `${o.name} (${o.uri})` })
         }
      }
   }, [value])
   return (
      <Select aria-label="Select schema"
                    isOpen={isExpanded}
                    onToggle={setExpanded}
                    selections={selected || []}
                    onClear={ () => {
                       setSelected(null)
                       setExpanded(false)
                       onChange(undefined)
                    }}
                    onSelect={ (e, newValue) => {
                       const schema = (newValue as Schema);
                       setSelected(schema)
                       setExpanded(false)
                       onChange(schema.uri)
                    }}
            >
      {options.map((option, index) => (
         <SelectOption key={index}
                       value={{ ...option, toString: () => `${option.name} (${option.uri})` }}
                       isDisabled={disabled.includes(option.uri)}/>
      ))}
      </Select>
   )
}