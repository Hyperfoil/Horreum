import React, { useState, useEffect } from 'react'

import { addOrUpdateExtractor, listExtractors } from '../domain/schemas/api.js'
import SchemaSelect from './SchemaSelect'

import {
   ActionGroup,
   Alert,
   Button,
   Form,
   FormGroup,
   Select,
   SelectOption,
   TextInput,
   Modal,
} from '@patternfly/react-core';

import {
   AddCircleOIcon,
} from '@patternfly/react-icons';

export default ({ value = [], onChange = newValue => {}, isReadOnly = false}) => {
   const [created, setCreated] = useState({})
   const onCreate = newValue => {
         setCreated({ accessor: newValue })
         setModalOpen(true)
   }
   const [isExpanded, setExpanded] = useState(false)
   const [options, setOptions] = useState(value.map(v => ({ accessor: v })))
   const [selected, setSelected] = useState(value)
   useEffect(() => {
      listExtractors().then(response => {
         setOptions(response)
      })
   }, [])
   const [modalOpen, setModalOpen] = useState(false)
   const [addFailed, setAddFailed] = useState(false)
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
                 var updated
                 if (selected.includes(newValue)) {
                    updated = selected.filter(o => o != newValue)
                 } else {
                    updated = [...selected, newValue]
                 }
                 setSelected(updated)
                 onChange(updated)
                 setExpanded(false)
              }}
      >
      { options.map((option, index) => (
         <SelectOption key={index} value={option.accessor} />
      ))}
      </Select>
      { selected !== null && selected.schema &&
         <div style={{ marginTop: "5px" }}>Valid for schemas:{'\u00A0'}
         { options.filter(e => e.accessor === selected.accessor).map(e => (<>
            <span style={{ border: "1px solid #888", borderRadius: "4px", padding: "4px", backgroundColor: "#f0f0f0"}}>{e.schema}</span>{'\u00A0'}
         </>)) }
         { !isReadOnly && selected.accessor && selected.accessor !== "" &&
            <Button variant="link" onClick={() => setModalOpen(true)}><AddCircleOIcon /></Button>
         }
         </div>
      }
      <Modal title="Create extractor"
             isOpen={modalOpen}
             onClose={() => setModalOpen(false) }>
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
                       onClick={e => {
                          addOrUpdateExtractor(created).then(response => {
                             setOptions([...options, created].sort())
                             setSelected([...selected, created.accessor])
                             setModalOpen(false)
                          }, e => {
                             setAddFailed(true)
                             setInterval(() => setAddFailed(false), 5000)
                          })
                       }}>Save</Button>
               <Button variant="secondary"
                       onClick={ () => {
                          setModalOpen(false)
                       }}>Cancel</Button>
         </ActionGroup>
         </Form>
         { addFailed &&
            <Alert variant="warning" title="Failed to add extractor" />
         }
       </Modal>
   </>)
}