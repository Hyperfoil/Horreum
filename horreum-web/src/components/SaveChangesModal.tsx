import {useState} from "react"

import {Bullseye, Button, Modal, Spinner, Split, SplitItem} from "@patternfly/react-core"

type SaveChangesModalProps = {
    isOpen: boolean
    onClose(): void
    onSave(): Promise<any>
    onReset(): void
}

export default function SaveChangesModal({isOpen, onClose, onSave, onReset}: SaveChangesModalProps) {
    const [saving, setSaving] = useState(false)
    return (
        <Modal
            variant="small"
            title="You have unsaved changes"
            titleIconVariant="warning"
            description="Do you want to save or discard them?"
            showClose={false}
            isOpen={isOpen}
        >
            {saving && (
                <Bullseye>
                    <Spinner/>
                </Bullseye>
            )}

            <Split hasGutter={true}>
                <SplitItem>
                    <Button isDisabled={saving} variant="secondary" onClick={onClose}>
                        Cancel
                    </Button>

                </SplitItem>
                <SplitItem>
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
                </SplitItem>

                <SplitItem>
                    <Button
                        isDisabled={saving}
                        variant="danger"
                        onClick={() => {
                            onReset()
                            onClose()
                        }}
                    >
                        Discard
                    </Button>

                </SplitItem>
            </Split>
        </Modal>
    )
}
