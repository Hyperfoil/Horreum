import { useState } from "react"

import { Bullseye, Button, Modal, Spinner } from "@patternfly/react-core"

type SaveChangesModalProps = {
    isOpen: boolean
    onClose(): void
    onSave?(): Promise<any>
    onReset(): void
}

export default function SaveChangesModal({ isOpen, onClose, onSave, onReset }: SaveChangesModalProps) {
    const [saving, setSaving] = useState(false)
    return (
        <Modal
            variant="small"
            title="Save changes?"
            description="Your changes haven't been saved."
            showClose={false}
            isOpen={isOpen}
        >
            {saving && (
                <Bullseye>
                    <Spinner />
                </Bullseye>
            )}
            <Button
                isDisabled={saving || !onSave}
                variant="primary"
                onClick={() => {
                    if (onSave) {
                        setSaving(true)
                        onSave().finally(() => {
                            setSaving(false)
                            onClose()
                        })
                    }
                }}
            >
                Save
            </Button>
            <Button isDisabled={saving} variant="secondary" onClick={onClose}>
                Cancel
            </Button>
            <Button
                isDisabled={saving}
                variant="secondary"
                onClick={() => {
                    onClose()
                    onReset()
                }}
            >
                Ignore
            </Button>
        </Modal>
    )
}
