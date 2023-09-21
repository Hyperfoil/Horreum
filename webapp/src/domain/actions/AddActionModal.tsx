import { useState } from "react"
import { Button, ButtonVariant, Modal } from "@patternfly/react-core"
import { Action as Action } from "../../api"
import { globalEventTypes } from "./reducers"
import ActionComponentForm from "./ActionComponentForm"

type AddActionModalProps = {
    isOpen: boolean
    onClose(): void
    onSubmit(action: Action): Promise<any>
}

const DEFAULT_ACTION = {
    id: -1,
    event: globalEventTypes[0][0],
    type: "http",
    testId: -1,
    config: { url: "" },
    secrets: {},
    active: true,
    runAlways: false,
}

function AddActionModal({isOpen, onClose, onSubmit}: AddActionModalProps) {
    const [action, setAction] = useState<Action>(DEFAULT_ACTION)
    const [isSaving, setSaving] = useState(false)
    const [isValid, setValid] = useState(true)

    const handleClose = () => {
        setAction(DEFAULT_ACTION)
        setSaving(false)
        setValid(true)
        onClose()
    }

    return (
        <Modal
            title="New Action"
            isOpen={isOpen}
            onClose={handleClose}
            actions={[
                <Button
                    key="save"
                    variant={ButtonVariant.primary}
                    isDisabled={isSaving || !isValid}
                    onClick={() => {
                        setSaving(true)
                        onSubmit(action).finally(handleClose)
                    }}
                >
                    Save
                </Button>,
                <Button key="cancel" variant={ButtonVariant.link} isDisabled={isSaving} onClick={handleClose}>
                    Cancel
                </Button>,
            ]}
        >
            <ActionComponentForm
                action={action}
        config={action.config}
                onUpdate={setAction}
                eventTypes={globalEventTypes}
                isTester={true}
                setValid={setValid}
            />
        </Modal>
    )
}

export default AddActionModal
