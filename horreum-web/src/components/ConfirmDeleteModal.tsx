import {useState} from "react"

import {Button, Modal, Spinner, Split, SplitItem, Stack, StackItem} from "@patternfly/react-core"

type ConfirmDeleteModalProps = {
    isOpen: boolean
    onClose(): void
    onDelete(): Promise<any>
    description: string
    extra?: string
}

export default function ConfirmDeleteModal({isOpen, onClose, onDelete, description, extra}: ConfirmDeleteModalProps) {
    const [deleting, setDeleting] = useState(false)
    return (
        <Modal variant="small" title="Confirm Delete" isOpen={isOpen} onClose={onClose}>

            <Stack hasGutter>
                <StackItem>
                    <div>Do you really want to delete {description}?</div>
                </StackItem>
                <StackItem>{extra}</StackItem>
                <StackItem>
                    <Split hasGutter>
                        <SplitItem>
                            <Button
                                isDisabled={deleting}
                                variant="danger"
                                onClick={() => {
                                    setDeleting(true)
                                    onDelete().finally(() => {
                                        setDeleting(false)
                                        onClose()
                                    })
                                }}
                            >
                                Delete
                                {deleting && (
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
