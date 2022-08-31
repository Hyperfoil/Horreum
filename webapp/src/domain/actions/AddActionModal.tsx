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

function AddActionModal(props: AddActionModalProps) {
    const [action, setAction] = useState<Action>(DEFAULT_ACTION)
    const [isSaving, setSaving] = useState(false)
    const [isValid, setValid] = useState(true)

    const onClose = () => {
        setAction(DEFAULT_ACTION)
        setSaving(false)
        setValid(true)
        props.onClose()
    }

    return (
        <Modal
            title="New Action"
            isOpen={props.isOpen}
            onClose={onClose}
            actions={[
                <Button
                    key="save"
                    variant={ButtonVariant.primary}
                    isDisabled={isSaving || !isValid}
                    onClick={() => {
                        setSaving(true)
                        props.onSubmit(action).finally(onClose)
                    }}
                >
                    Save
                </Button>,
                <Button key="cancel" variant={ButtonVariant.link} isDisabled={isSaving} onClick={onClose}>
                    Cancel
                </Button>,
            ]}
        >
            <ActionComponentForm
                action={action}
                onUpdate={setAction}
                eventTypes={globalEventTypes}
                isTester={true}
                setValid={setValid}
            />
        </Modal>
    )
}

export default AddActionModal
