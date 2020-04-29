import React, { useState, useEffect } from 'react'

import { addOrUpdateExtractor, listExtractors } from '../domain/schemas/api.js'
import SchemaSelect from './SchemaSelect'

import {
   ActionGroup,
   Alert,
   Button,
   Form,
   FormGroup,
   Radio,
   Select,
   SelectOption,
   TextInput,
   Modal,
} from '@patternfly/react-core';

import {
   AddCircleOIcon,
} from '@patternfly/react-icons';

function distinctSorted(list, selector) {
   return Array.from(new Set(list.map(selector)))
         .map(a => list.find(o => selector(o) === a)) // distinct
         .sort((a, b) => selector(a).localeCompare(selector(b)))
}

function baseName(name) {
   return name.endsWith("[]") ? name.substring(0, name.length - 2) : name
}

export default ({ value = [], onChange = newValue => {}, isReadOnly = false}) => {
   const [created, setCreated] = useState({})
   const onCreate = newValue => {
         setCreated({ accessor: newValue })
         setDisabledSchemas([])
         setCreateOpen(true)
   }
   const [disabledSchemas, setDisabledSchemas] = useState([])
   const [isExpanded, setExpanded] = useState(false)
   const [options, setOptions] = useState(value.map(v => ({ accessor: v })))
   const [selected, setSelected] = useState(value)
   useEffect(() => {
      listExtractors().then(response => {
         setOptions(response)
      })
   }, [])
   const [createOpen, setCreateOpen] = useState(false)
   const [addFailed, setAddFailed] = useState(false)

   const [variantOpen, setVariantOpen] = useState(false)
   const [variant, setVariant] = useState(0)
   const [addedOption, setAddedOption] = useState(null)
   const openVariantModal = newOption => {
      setAddedOption(newOption)
      setVariant(newOption.accessor.endsWith("[]") ? 1 : 0)
      setVariantOpen(true)
   }

   return (<>
      <Select variant="typeaheadmulti"
              aria-label="Select accessor"
              ariaLabelTypeAhead="Select accessor"
              placeholderText="Select accessor"
              isCreatable={true}
              onCreateOption={onCreate}
              isExpanded={isExpanded}
              onToggle={setExpanded}
              selections={selected}
              isDisabled={isReadOnly}
              onClear={ () => {
                 setSelected([])
                 setExpanded(false)
                 onChange([])
              }}
              onSelect={ (e, newValue) => {
                 if (!options.find(o => o.accessor === newValue)) {
                     return // this is the create
                 }
                 setExpanded(false)
                 let base = baseName(newValue)
                 let array = base + "[]"
                 let updated
                 if (selected.includes(newValue)) {
                    updated = selected.filter(o => o != newValue)
                 } else if (selected.includes(base)) {
                    updated = [...selected.filter(o => o != base), newValue]
                 } else if (selected.includes(array)) {
                    updated = [...selected.filter(o => o != array), newValue]
                 } else {
                    openVariantModal(options.filter(o => o.accessor === newValue)[0])
                    return
                 }
                 setSelected(updated)
                 onChange(updated)
              }}
      >
      { distinctSorted(options.concat(value.map(v => ({ accessor: v}))), o => o.accessor)
         .map((option, index) => (<SelectOption key={index} value={option.accessor} />)
      )}
      </Select>
      { selected && selected.map(s => {
         const distinctSchemaOptions = distinctSorted(options.filter(o => o.accessor === s), o => o.schema)
         return (
         <div key={s} style={{ marginTop: "5px" }}>
            <span style={{ border: "1px solid #888", borderRadius: "4px", padding: "4px", backgroundColor: "#f0f0f0"}}>{s}</span> is valid for schemas:{'\u00A0'}
            { distinctSchemaOptions.map((o, i) => (<>
               <span key={s + "-" + i} style={{ border: "1px solid #888", borderRadius: "4px", padding: "4px", backgroundColor: "#f0f0f0"}}>{o.schema}</span>{'\u00A0'}
            </>)) }
            { !isReadOnly && s !== "" &&
               <Button variant="link" onClick={() => {
                  setCreated({ accessor: s })
                  setDisabledSchemas(distinctSchemaOptions.map(o => o.schema))
                  setCreateOpen(true)
               }}><AddCircleOIcon /></Button>
            }
         </div>
      )})}
      <Modal title="Create extractor"
             isOpen={createOpen}
             onClose={() => setCreateOpen(false) }>
         <Form isHorizontal={true}>
            <FormGroup label="Accessor" isRequired={true} fieldId="extractor-accessor">
               <TextInput value={ created.accessor || "" }
                          isRequired
                          id="extractor-accessor"
                          name="extractor-accessor"
                          isValid={ created.accessor && created.accessor !== "" }
                          onChange={ value => setCreated({ ...created, accessor: value})}
                />
            </FormGroup>
            <FormGroup label="Schema" isRequired={true} fieldId="extractor-schema" >
               <SchemaSelect value={ selected.schema }
                             id="extractor-schema"
                             disabled={ disabledSchemas }
                             onChange={ value => { setCreated({ ...created, schema: value, toString: () => created.accessor })}} />
            </FormGroup>
            <FormGroup label="JSON path" isRequired={true} fieldId="extractor-jsonpath">
               <TextInput value={ created.jsonpath || "" }
                          isRequired
                          id="extractor-jsonpath"
                          name="extractor-jsonpath"
                          isValid={ created.jsonpath && created.jsonpath !== "" && !created.jsonpath.startsWith("$") }
                          onChange={ value => setCreated({ ...created, jsonpath: value})}
              />
            </FormGroup>
            <ActionGroup>
               <Button variant="primary"
                       onClick={() => {
                          addOrUpdateExtractor(created).then(response => {
                             setCreateOpen(false)
                             openVariantModal(created)
                          }, () => {
                             setAddFailed(true)
                             setInterval(() => setAddFailed(false), 5000)
                          })
                       }}>Save</Button>
               <Button variant="secondary"
                       onClick={ () => {
                          setCreateOpen(false)
                       }}>Cancel</Button>
            </ActionGroup>
         </Form>
         { addFailed &&
            <Alert variant="warning" title="Failed to add extractor" />
         }
      </Modal>
      <Modal isSmall title="Select variant"
             isOpen={variantOpen}
             onClose={() => setVariantOpen(false)}>
         <Radio isChecked={variant == 0}
                id="first-match"
                name="first-match"
                label="First match"
                onChange={() => setVariant(0)}/>
         <Radio isChecked={variant == 1}
                id="all-matches"
                name="all-matches"
                label="All matches (as array)"
                onChange={() => setVariant(1)}/>
         <ActionGroup>
            <Button variant="primary"
                    onClick={() => {
                       setVariantOpen(false)
                       let base = baseName(addedOption.accessor)
                       let name = variant == 0 ? base : base + "[]"
                       setOptions([...options, { ...addedOption, accessor: name }])
                       let updated = [...selected, name]
                       setSelected(updated)
                       onChange(updated)
                    }}>Select</Button>
            <Button variant="secondary"
                    onClick={() => setVariantOpen(false)}
                    >Cancel</Button>
         </ActionGroup>
      </Modal>
   </>)
}