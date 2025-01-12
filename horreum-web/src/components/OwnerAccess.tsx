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

export default function OwnerAccess({owner, access, readOnly, onUpdate}: OwnerAccessProps) {
    const [modalOpen, setModalOpen] = useState(false)
    const [newOwner, setNewOwner] = useState<string>(owner)
    const [newAccess, setNewAccess] = useState<Access>(access)
    return (
        <>
            <AccessIcon access={access} />
            {"\u00A0\u2014\u00A0"}
            {teamToName(owner)}
            {"\u00A0\u00A0"}
            {!readOnly && (
                <Button
                    icon={<EditIcon/>}
                    variant="link"
                    onClick={() => {
                        setNewAccess(access)
                        setNewOwner(owner)
                        setModalOpen(true)
                    }}
                />
            )}
            <ChangeAccessModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                owner={newOwner}
                access={newAccess}
                onOwnerChange={owner => setNewOwner(owner)}
                onAccessChange={access => setNewAccess(access)}
                onUpdate={() => {
                onUpdate(newOwner, newAccess)
                setModalOpen(false)
                }}
            />
        </>
    )
}
