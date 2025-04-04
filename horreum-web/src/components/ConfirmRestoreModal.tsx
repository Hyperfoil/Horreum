import {useState} from "react"

import {Button, Modal, Spinner, Split, SplitItem, Stack, StackItem} from "@patternfly/react-core"

type ConfirmRestoreModalProps = {
    isOpen: boolean
    onClose(): void
    onRestore(): Promise<any>
    description: string
    extra?: string
    restoreButtonText?: string
}

export default function ConfirmRestoreModal({isOpen, onClose, onRestore, description, extra, restoreButtonText}: ConfirmRestoreModalProps) {
    const [restoring, setRestoring] = useState(false)
    return (
        <Modal variant="small" title="Confirm Restore" isOpen={isOpen} onClose={onClose}>

            <Stack hasGutter>
                <StackItem>
                    <div>Do you really want to { restoreButtonText?.toLowerCase() ?? "restore"} {description}?</div>
                </StackItem>
                <StackItem>{extra}</StackItem>
                <StackItem>
                    <Split hasGutter>
                        <SplitItem>
                            <Button
                                isDisabled={restoring}
                                variant="primary"
                                onClick={() => {
                                    setRestoring(true)
                                    onRestore().finally(() => {
                                        setRestoring(false)
                                        onClose()
                                    })
                                }}
                            >
                                {restoreButtonText ?? "Restore"}
                                {restoring && (
                                    <>
                                        {" "}
                                        <Spinner size="md"/>
                                    </>
                                )}
                            </Button></SplitItem>
                        <SplitItem>
                            <Button variant="secondary" onClick={onClose}>
                                Cancel
                            </Button>
                        </SplitItem>
                    </Split>

                </StackItem>
            </Stack>
        </Modal>
    )
}
