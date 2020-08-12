import React, { useState } from 'react';
import {
    Button,
    ButtonVariant,
    Form,
    FormGroup,
    FormSelectOption,
    FormSelect,
    Modal,
    TextInput,
} from '@patternfly/react-core';
import { Hook } from './reducers';
import TestSelect, { SelectedTest } from '../../components/TestSelect'

const eventTypes = ["test/new","run/new"]

const isValidUrl = (string: string) => {
    try {
      new URL(string);
      return true;
    } catch (_) {
      return false;
    }
  }

const allValid = { url: true, type: true, target: true }

export default ({isOpen=false,onCancel=()=>{}, onSubmit=(validation: Hook)=>{}})=>{

    const [url,setUrl] = useState("");
    const [eventType,setEventType] = useState(eventTypes[0])
    const allTests: SelectedTest = { id: -1, toString: () => "All tests" }
    const [target,setTarget] = useState<SelectedTest>(allTests);

    const [valid,setValid] = useState(allValid)

    const validate = () => {
        const rtrn = {
            url: isValidUrl(url),
            type: true,
            target: true,
        }
        setValid(rtrn)
        return rtrn.url && rtrn.type && rtrn.target
    }

    const checkSubmit = ()=>{
        const isValid = validate();
        if(isValid){
            const toSubmit: Hook = {
                id: 0,
                url: url.trim(),
                type: eventType,
                target : target.id,
                active: true
            }
            onSubmit(toSubmit)
        }
    }
    return (
        <Modal
            title="New Hook"
            isOpen={isOpen}
            onClose={onCancel}
            actions={[
                <Button key="save" variant={ButtonVariant.primary} onClick={checkSubmit}>Save</Button>,
                <Button key="cancel" variant={ButtonVariant.link} onClick={(e)=>{setValid(allValid); onCancel();}}>Cancel</Button>
            ]}
        >
            <Form isHorizontal={true}>
                <FormGroup label="Url" validated={valid.url ? "default" : "error"} isRequired={true} fieldId="url" helperText="url (with protocol) for POST callback" helperTextInvalid="url (with protocol) for POST callback">
                    <TextInput
                        value={url}
                        isRequired
                        type="text"
                        id="url"
                        aria-describedby="url-helper"
                        name="url"
                        validated={valid.url ? "default" : "error"}
                        onChange={e=>setUrl(e)}
                    />
                </FormGroup>
                <FormGroup label="Event Type" validated={valid.type ? "default" : "error"} isRequired={true} fieldId="type" helperText="event type for callback" helperTextInvalid="event type for callback">
                    <FormSelect
                        id="type"
                        validated={"default"}
                        value={eventType}
                        onChange={ setEventType }
                        aria-label="Event Type"
                        >
                        {eventTypes.map((option, index)=>{
                            return (<FormSelectOption
                                key={index}
                                value={option}
                                label={option}  />)
                        })}
                    </FormSelect>
                </FormGroup>
                {
                    eventType === "run/new" &&
                    <FormGroup label="Test" isRequired={true} helperText="Which tests should this hook fire on" fieldId="target">
                        <TestSelect
                            selection={target}
                            onSelect={ setTarget }
                            extraOptions={[ allTests ]}
                            direction="up"/>
                    </FormGroup>
                }
            </Form>


        </Modal>
    )
}