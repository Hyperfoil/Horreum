import React, {useState} from "react"

import {
    ActionGroup,
    Button, Form,
    FormGroup, Modal, TextInput
} from "@patternfly/react-core"

type ConfirmDeleteModalProps = {
    isOpen: boolean
    onClose(): void
    onDelete(): Promise<any>
    description: string
    extra?: string
}

export default function VerifyBackendModal({isOpen, onClose}: ConfirmDeleteModalProps) {
    const [name, setName] = useState('');

    const handleNameChange = (name: string) => {
        setName(name);
    };

    return (
        <Modal variant="medium" title="Verify Backend" isOpen={isOpen} onClose={onClose}>

            <Form isHorizontal>
                <FormGroup
                    label="name"
                    isRequired
                    fieldId="horizontal-form-name"
                    helperText="Please an name for the backend"
                >
                    <TextInput
                        value={name}
                        isRequired
                        type="text"
                        id="horizontal-form-name"
                        aria-describedby="horizontal-form-name-helper"
                        name="horizontal-form-name"
                        onChange={handleNameChange}
                    />
                </FormGroup>
                <ActionGroup>
                    <Button variant="primary">Verify</Button>
                </ActionGroup>
            </Form>
        </Modal>
    )
}
