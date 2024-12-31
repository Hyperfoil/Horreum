import { Button, ClipboardCopy, Content, Modal } from "@patternfly/react-core"

type ShareLinkModalProps = {
    isOpen: boolean
    onClose(): void
    link: string
    isTester: boolean
    onReset(): void
    onDrop(): void
}

export default function ShareLinkModal({ isOpen, onClose, link, isTester, onReset, onDrop }: ShareLinkModalProps) {
    return (
        <Modal variant="small" title="Shareable link" isOpen={isOpen} onClose={onClose}>
            {link ? (
                <>
                    <ClipboardCopy isReadOnly>{window.location.origin + link}</ClipboardCopy>
                    {isTester && (
                        <>
                            <Button variant="secondary" onClick={onReset}>
                                Reset link
                            </Button>
                            <Button variant="secondary" onClick={onDrop}>
                                Drop link
                            </Button>
                        </>
                    )}
                </>
            ) : (
                <>
                    <Content component="p">No shareable link yet.</Content>
                    <Button variant="primary" onClick={onReset}>
                        Create link
                    </Button>
                </>
            )}
        </Modal>
    )
}
