import React, { useRef, MutableRefObject } from 'react';
import { useSelector } from 'react-redux'
import {
    Button,
    Form,
    ActionGroup,
    FormGroup,
    Tab,
    Tabs,
    TextInput,
} from '@patternfly/react-core';
import {
    ArrowAltCircleDownIcon,
    ArrowAltCircleUpIcon,
    OutlinedTimesCircleIcon
} from '@patternfly/react-icons';
import Editor, { ValueGetter } from '../../components/Editor/monaco/Editor'


import { isTesterSelector } from '../../auth'

import Accessors from '../../components/Accessors'
import { View } from './reducers';

function swap(array: any[], i1: number, i2: number) {
    const temp = array[i1]
    array[i1] = array[i2]
    array[i2] = temp
}

type ViewsProps = {
    view: View,
    onViewChange(newView: View): void,
    updateRendersRef: MutableRefObject<(() => void) | undefined>
}

export default ({ view, onViewChange, updateRendersRef }: ViewsProps) => {
    const isTester = useSelector(isTesterSelector)
    const renderRefs = useRef(new Array<ValueGetter | undefined>(view.components.length));
    const updateRenders = () => view.components.forEach((c, i) => {
         c.render = renderRefs.current[i]?.getValue()
    })
    updateRendersRef.current = updateRenders;


    return (<>
        { /* TODO: display more than default view */ }
        { /* TODO: make tabs secondary, but boxed? */ }
        <Tabs>
            <Tab key="__default" eventKey={0} title="Default" />
            <Tab key="__new" eventKey={1} title="+" />
        </Tabs>
        { (!view.components || view.components.length === 0) && "The view is not defined" }
        { view.components && view.components.map((c, i) => (
            <div style={{ display: "flex "}}>
               <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", float: "left", marginBottom: "25px" }}>
                   <FormGroup label="Header" fieldId="header">
                     <TextInput value={ c.headerName || "" } placeholder="e.g. 'Run duration'"
                                id="header"
                                onChange={ value => { c.headerName = value; onViewChange({ ...view}) }}
                                isValid={ !!c.headerName && c.headerName.trim() !== "" }
                                isReadOnly={!isTester} />
                   </FormGroup>
                   <FormGroup label="Accessors" fieldId="accessor">
                     <Accessors
                                value={ (c.accessors && c.accessors.split(/[,;] */).map(a => a.trim()).filter(a => a.length !== 0)) || [] }
                                onChange={ value => { c.accessors = value.join(";"); onViewChange({ ...view }) }}
                                isReadOnly={!isTester} />
                   </FormGroup>
                   <FormGroup label="Rendering" fieldId="rendering">
                     <div style={{ minHeight: "100px", height: "100px", resize: "vertical", overflow: "auto" }}>
                         <Editor value={ (c.render && c.render.toString()) || "" }
                                 setValueGetter={e => { renderRefs.current[i] = e }}
                                 options={{ wordWrap: 'on', wrappingIndent: 'DeepIndent', language: 'typescript', readOnly: !isTester }} />
                     </div>
                   </FormGroup>
               </Form>
               { isTester &&
               <div style={{ width: "40px", float: "right", display: "table-cell", position: "relative", marginBottom: "25px" }}>
                   <Button style={{width: "100%", marginTop: "4px"}}
                           variant="plain"
                           isDisabled={ i === 0 }
                           onClick={ () => {
                              updateRenders()
                              swap(view.components, i - 1, i)
                              swap(renderRefs.current, i - 1, i)
                              c.headerOrder = i - 1;
                              view.components[i].headerOrder = i;
                              onViewChange({ ...view })
                   }} ><ArrowAltCircleUpIcon /></Button>
                   <Button style={{width: "100%", position: "absolute", left: "0px", top: "38%"}}
                           variant="plain"
                           onClick={ () => {
                              view.components.splice(i, 1)
                              renderRefs.current.splice(i, 1)
                              view.components.forEach((c, i) => c.headerOrder = i)
                              onViewChange({ ...view})
                   }}><OutlinedTimesCircleIcon style={{color: "#a30000"}}/></Button>
                   <Button style={{width: "100%", position: "absolute", left: "0px", bottom: "4px"}}
                           variant="plain"
                           isDisabled={ i === view.components.length - 1 }
                           onClick={ () => {
                              updateRenders()
                              swap(view.components, i + 1, i)
                              swap(renderRefs.current, i + 1, i)
                              c.headerOrder = i + 1;
                              view.components[i].headerOrder = i;
                              onViewChange({ ...view})
                   }} ><ArrowAltCircleDownIcon /></Button>
               </div>
               }
            </div>
        ))}
        { isTester &&
        <ActionGroup>
            <Button onClick={ () => {
               const components = view.components || []
               components.push({
                   headerName: "",
                   accessors: "",
                   render: "",
                   headerOrder: components.length
               })
               onViewChange({ ...view, components })
               renderRefs.current.push(undefined)
            }} >Add component</Button>

        </ActionGroup>
        }
    </>)
}