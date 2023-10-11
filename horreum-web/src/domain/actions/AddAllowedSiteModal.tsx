import { useState } from "react"
import { Button, ButtonVariant, Form, FormGroup, Modal, TextInput } from "@patternfly/react-core"

type AddAllowedSiteModalProps = {
    isOpen: boolean
    onClose(): void
    onSubmit(prefix: string): Promise<any>
}

export default function AddAllowedSiteModal(props: AddAllowedSiteModalProps) {
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
            title="Add allowed site"
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
                    label="Site prefix"
                    validated={isValid ? "default" : "warning"}
                    isRequired={true}
                    fieldId="prefix"
                    helperText="Prefix (protocol, domain and optionally port and path) for all URLs used in HTTP Actions"
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
