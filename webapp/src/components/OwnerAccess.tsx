import { useState } from "react"
import { Button } from "@patternfly/react-core"
import { EditIcon } from "@patternfly/react-icons"

import { teamToName } from "../auth"
import { Access } from "../api"
import AccessIcon from "./AccessIcon"
import ChangeAccessModal from "./ChangeAccessModal"

type OwnerAccessProps = {
    owner: string
    access: Access
    readOnly: boolean
    onUpdate(owner: string, access: Access): void
}

export default function OwnerAccess(props: OwnerAccessProps) {
    const [modalOpen, setModalOpen] = useState(false)
    const [newOwner, setNewOwner] = useState<string>(props.owner)
    const [newAccess, setNewAccess] = useState<Access>(props.access)
    return (
        <>
            <AccessIcon access={props.access} />
            {"\u00A0\u2014\u00A0"}
            {teamToName(props.owner)}
            {"\u00A0\u00A0"}
            {!props.readOnly && (
                <Button
                    variant="link"
                    onClick={() => {
                        setNewAccess(props.access)
                        setNewOwner(props.owner)
                        setModalOpen(true)
                    }}
                >
                    <EditIcon />
                </Button>
            )}
            <ChangeAccessModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                owner={newOwner}
                access={newAccess}
                onOwnerChange={owner => setNewOwner(owner)}
                onAccessChange={access => setNewAccess(access)}
                onUpdate={() => {
                    props.onUpdate(newOwner, newAccess)
                    setModalOpen(false)
                }}
            />
        </>
    )
}
