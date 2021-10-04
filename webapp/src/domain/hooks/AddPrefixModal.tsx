import React, { useState } from "react"
import { Button, ButtonVariant, Form, FormGroup, Modal, TextInput } from "@patternfly/react-core"

type AddPrefixModalProps = {
    isOpen: boolean
    onClose(): void
    onSubmit(prefix: string): Promise<any>
}

function AddPrefixModal(props: AddPrefixModalProps) {
    const [prefix, setPrefix] = useState("")
    const [isSaving, setSaving] = useState(false)
    const isValid = prefix === "" || prefix.startsWith("http://") || prefix.startsWith("https://")
    const onClose = () => {
        setPrefix("")
        setSaving(false)
        props.onClose()
    }
    return (
        <Modal
            title="Add allowed hook prefix"
            isOpen={props.isOpen}
            onClose={onClose}
            actions={[
                <Button
                    key="save"
                    isDisabled={!isValid || isSaving}
                    variant={ButtonVariant.primary}
                    onClick={() => {
                        setSaving(true)
                        props.onSubmit(prefix).finally(onClose)
                    }}
                >
                    Save
                </Button>,
                <Button key="cancel" isDisabled={isSaving} variant={ButtonVariant.link} onClick={onClose}>
                    Cancel
                </Button>,
            ]}
        >
            <Form isHorizontal={true}>
                <FormGroup
                    label="Prefix"
                    validated={isValid ? "default" : "warning"}
                    isRequired={true}
                    fieldId="prefix"
                    helperText="Prefix for all URLs used in webhooks"
                    helperTextInvalid="The prefix should be either empty, or start with 'http://' or 'https://'"
                >
                    <TextInput
                        value={prefix}
                        isDisabled={isSaving}
                        type="text"
                        id="prefix"
                        aria-describedby="url-helper"
                        name="url"
                        validated={isValid ? "default" : "error"}
                        onChange={setPrefix}
                        onKeyDown={e => {
                            if (e.key === "Enter") e.preventDefault()
                        }}
                    />
                </FormGroup>
            </Form>
        </Modal>
    )
}

export default AddPrefixModal
