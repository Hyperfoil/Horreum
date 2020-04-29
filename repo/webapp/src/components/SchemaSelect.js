import React, { useState, useEffect } from 'react'

import { all as allSchemas } from '../domain/schemas/api.js'

import {
   Select,
   SelectOption,
} from '@patternfly/react-core';

/* This is going to be a complex component with modal for Extractor definition */
export default ({ value = "", onChange = newValue => {}, disabled = []}) => {
   const [isExpanded, setExpanded] = useState(false)
   const [selected, setSelected] = useState(value)
   const [options, setOptions] = useState([])
   useEffect(() => {
      // TODO: this is fetching all schemas including the schema JSONs
      allSchemas().then(response => {
         const schemas = response.map(s => { return { name: s.name, uri: s.uri }; })
         setOptions(schemas)
         if ((selected === null || selected === "") && schemas.length > 0) {
            onChange(schemas[0].uri)
         }
      })
   }, [])
   useEffect(() => {
      if (value && value !== "") {
         let o = options.find(s => s.uri === value)
         if (o && o !== selected) {
            setSelected({ ...o, toString: () => `${o.name} (${o.uri})` })
         }
      }
   }, [value])
   return (
      <Select aria-label="Select schema"
                    isExpanded={isExpanded}
                    onToggle={setExpanded}
                    selections={selected}
                    onClear={ () => {
                       setSelected(null)
                       setExpanded(false)
                       onChange(null)
                    }}
                    onSelect={ (e, newValue) => {
                       setSelected(newValue)
                       setExpanded(false)
                       onChange(newValue.uri)
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