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
export default function SchemaSelect({ value = "", onChange = (_: string) => {}, disabled = []}: SchemaSelectProps) {
   const [isExpanded, setExpanded] = useState(false)
   const [options, setOptions] = useState<Schema[]>([])
   useEffect(() => {
      if (value !== "" && options.length > 0) {
         return
      }
      // TODO: this is fetching all schemas including the schema JSONs
      allSchemas().then((response: Schema[]) => {
         const schemas = response.map(s => { return { name: s.name, uri: s.uri, toString: () => `${s.name} (${s.uri})` }; })
         setOptions(schemas)
         if (value === "" && schemas.length > 0) {
            onChange(schemas[0].uri)
         }
      })
   }, [onChange, value, options])
   return (
      <Select aria-label="Select schema"
                    isOpen={isExpanded}
                    onToggle={setExpanded}
                    selections={options.find(o => o.uri === value) || []}
                    onClear={ () => {
                       setExpanded(false)
                       onChange(undefined)
                    }}
                    onSelect={ (e, newValue) => {
                       const schema = (newValue as Schema);
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