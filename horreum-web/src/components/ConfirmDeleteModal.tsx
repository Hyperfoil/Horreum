import { useState } from "react"

import { Button, Modal, Spinner } from "@patternfly/react-core"

type ConfirmDeleteModalProps = {
    isOpen: boolean
    onClose(): void
    onDelete(): Promise<any>
    description: string
    extra?: string
}

export default function ConfirmDeleteModal({ isOpen, onClose, onDelete, description, extra }: ConfirmDeleteModalProps) {
    const [deleting, setDeleting] = useState(false)
    return (
        <Modal variant="small" title="Confirm deletion" isOpen={isOpen} onClose={onClose}>
            <div>Do you really want to delete {description}?</div>
            {extra}
            <br />
            <div>
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
                            <Spinner size="md" />
                        </>
                    )}
                </Button>
                <Button variant="secondary" onClick={onClose}>
                    Cancel
                </Button>
            </div>
        </Modal>
    )
}
