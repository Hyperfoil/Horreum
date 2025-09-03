import {useState} from "react"

import {Button, Spinner, Split, SplitItem, Stack, StackItem} from '@patternfly/react-core';
import {Modal} from '@patternfly/react-core/deprecated';

type ConfirmDeleteModalProps = {
    isOpen: boolean
    onClose(): void
    onDelete(): Promise<any>
    description: string
    extra?: string
    deleteButtonText?: string
}

export default function ConfirmDeleteModal({isOpen, onClose, onDelete, description, extra, deleteButtonText}: ConfirmDeleteModalProps) {
    const [deleting, setDeleting] = useState(false)
    return (
        <Modal variant="small" title="Confirm Delete" isOpen={isOpen} onClose={onClose}>

            <Stack hasGutter>
                <StackItem>
                    <div>Do you really want to { deleteButtonText?.toLowerCase() ?? "delete"} {description}?</div>
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
                                {deleteButtonText ?? "Delete"}
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
