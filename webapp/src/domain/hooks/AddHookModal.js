import React, { useState } from 'react';
import {
    Button,
    ButtonVariant,
    Card,
    CardHeader,
    CardBody,
    Form,
  FormGroup,
  TextInput,
  TextArea,
  FormSelectionOption,
  FormSelect,
    Modal,
    PageSection,
    Toolbar,
    ToolbarGroup,
    ToolbarItem,
    ToolbarSection,
} from '@patternfly/react-core';

const eventTypes = ["new/test","new/run"]

const isValidUrl = (string) => {
    try {
      new URL(string);
      return true;
    } catch (_) {
      return false;  
    }
  }

const allValid = {url:true,type:true,target:true}

export default ({isOpen=false,onCancel=()=>{},onSubmit=()=>{}})=>{

    const [url,setUrl] = useState("");
    const [eventType,setEventType] = useState("")
    const [target,setTarget] = useState("");

    const [valid,setValid] = useState(allValid)


    
    const validate = ()=>{
        const rtrn={}
        rtrn.url = isValidUrl(url)
        rtrn.type = eventTypes.includes(eventType);
        rtrn.target = target === "" || target === "-1" || /^\d*\.?\d*$/.test(target)
        setValid(rtrn)
        return rtrn.url && rtrn.type && rtrn.target
    }

    const checkSubmit = ()=>{
        const isValid = validate();
        if(isValid){
            const convertedType = isNaN(parseInt(target)) ? -1 : parseInt(target)
            const toSubmit = {url:url.trim(),type:eventType.trim(),target : convertedType , active: true}
            onSubmit(toSubmit)
        }
    }

    return (
        <Modal
            title="New Hook"
            isOpen={isOpen}
            onClose={e=>onCancel()}
            actions={[
                <Button key="save" variant={ButtonVariant.primary} onClick={checkSubmit}>Save</Button>,
                <Button key="cancel" variant={ButtonVariant.link} onClick={(e)=>{setValid(allValid); onCancel();}}>Cancel</Button>
            ]}
            isFooterLeftAligned={false}
        >
            <Form isHorizontal={true}>
                <FormGroup label="Url" isValid={valid.url} isRequired={true} fieldId="url" helperText="url (with protocol) for POST callback" helperTextInvalid="url (with protocol) for POST callback">
                    <TextInput
                        value={url}
                        isRequired
                        type="text"
                        id="url"
                        aria-describedby="url-helper"
                        name="url"
                        isValid={valid.url}
                        onChange={e=>setUrl(e)}
                    />
                </FormGroup>
                <FormGroup label="Event Type" isValid={valid.type} isRequired={true} fieldId="type" helperText="event type for callback" helperTextInvalid="event type for callback">
                    <TextInput
                        value={eventType}
                        isRequired
                        type="text"
                        id="type"
                        aria-describedby="type-helper"
                        name="type"
                        isValid={valid.type}
                        onChange={e=>setEventType(e)}
                    />
                </FormGroup>
                <FormGroup label="Target" isValid={valid.target} isRequired={true} fieldId="target" helperText="event target id, -1 for ALL" helperTextInvalid="target is empty, -1, or a positive integer">
                    <TextInput
                        value={target}
                        isRequired
                        type="text"
                        id="type"
                        aria-describedby="target-helper"
                        name="target"
                        isValid={valid.target}
                        onChange={e=>setTarget(e)}
                    />
                </FormGroup>
            </Form>


        </Modal>
    )
}