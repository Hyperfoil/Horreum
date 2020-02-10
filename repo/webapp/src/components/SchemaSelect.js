import React, { useState, useEffect } from 'react'

import { useDispatch } from 'react-redux'

import { all as allSchemas } from '../domain/schemas/api.js'

import {
   Select,
   SelectOption,
} from '@patternfly/react-core';

/* This is going to be a complex component with modal for Extractor definition */
export default ({ value = "", onChange = newValue => {}}) => {
   const [isExpanded, setExpanded] = useState(false)
   const [selected, setSelected] = useState(value)
   const [options, setOptions] = useState([])
   useEffect(() => {
      // TODO: this is fetching all schemas including the schema JSONs
      allSchemas().then(response => {
         const schemas = response.map(s => { return { name: s.name, uri: s.uri }; })
         setOptions(schemas)
         if (schemas.length > 0) {
            onChange(schemas[0].uri)
         }
      })
   }, [])
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
         <SelectOption key={index} value={{ ...option, toString: () => `${option.name} (${option.uri})` }} />
      ))}
      </Select>
   )
}