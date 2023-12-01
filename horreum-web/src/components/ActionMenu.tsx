import { useState, useEffect, ReactElement, ReactNode } from "react"

import {
	Dropdown,
	DropdownItem,
	KebabToggle
} from '@patternfly/react-core/deprecated';

import { useTester } from "../auth"
import { Access } from "../api"

import ShareLinkModal from "./ShareLinkModal"
import ChangeAccessModal from "./ChangeAccessModal"
import ConfirmDeleteModal from "./ConfirmDeleteModal"

interface MenuItemProvider<C> {
    (props: ActionMenuProps, isTester: boolean, close: () => void, config: C): {
        item: ReactElement
        modal: ReactNode
    }
}

export type MenuItem<C> = [MenuItemProvider<C>, C]

export type ActionMenuProps = {
    id: number
    owner: string
    access: Access
    description: string
    items: MenuItem<any>[]
}

type ShareLinkConfig = {
    token?: string
    tokenToLink(id: number, token: string): string
    onTokenReset(id: number): void
    onTokenDrop(id: number): void
}

export function useShareLink(config: ShareLinkConfig): MenuItem<ShareLinkConfig> {
    const [shareLinkModalOpen, setShareLinkModalOpen] = useState(false)
    return [
        (props: ActionMenuProps, isTester: boolean, close: () => void, config: ShareLinkConfig) => {
            return {
                item: (
                    <DropdownItem
                        key="link"
                        isDisabled={props.access === Access.Public || (!config.token && !isTester)}
                        onClick={() => {
                            close()
                            setShareLinkModalOpen(true)
                        }}
                    >
                        Shareable link
                    </DropdownItem>
                ),
                modal: (
                    <ShareLinkModal
                        key="link"
                        isOpen={shareLinkModalOpen}
                        onClose={() => setShareLinkModalOpen(false)}
                        isTester={isTester}
                        link={config.token ? config.tokenToLink(props.id, config.token) : ""}
                        onReset={() => config.onTokenReset(props.id)}
                        onDrop={() => config.onTokenDrop(props.id)}
                    />
                ),
            }
        },
        config,
    ]
}

type ChangeAccessConfig = {
    onAccessUpdate(id: number, owner: string, access: Access): void
}

function ChangeAccessProvider(
    props: ActionMenuProps,
    isTester: boolean,
    close: () => void,
    config: ChangeAccessConfig
) {
    const [changeAccessModalOpen, setChangeAccessModalOpen] = useState(false)
    const [newAccess, setNewAccess] = useState<Access>(Access.Public)
    const [newOwner, setNewOwner] = useState<string>("")

    useEffect(() => {
        setNewOwner(props.owner)
    }, [props.owner])
    useEffect(() => {
        setNewAccess(props.access)
    }, [props.access])

    const onChangeAccessClose = () => {
        setNewOwner(props.owner)
        setNewAccess(props.access)
        setChangeAccessModalOpen(false)
    }
    return {
        item: (
            <DropdownItem
                key="access"
                onClick={() => {
                    close()
                    setChangeAccessModalOpen(true)
                }}
                isDisabled={!isTester}
            >
                Change access
            </DropdownItem>
        ),
        modal: (
            <ChangeAccessModal
                key="changeAccess"
                isOpen={changeAccessModalOpen}
                onClose={onChangeAccessClose}
                owner={newOwner}
                onOwnerChange={setNewOwner}
                access={newAccess}
                onAccessChange={setNewAccess}
                onUpdate={() => {
                    config.onAccessUpdate(props.id, newOwner, newAccess)
                    onChangeAccessClose()
                }}
            />
        ),
    }
}

export function useChangeAccess(config: ChangeAccessConfig): MenuItem<ChangeAccessConfig> {
    return [ChangeAccessProvider, config]
}

type DeleteConfig = {
    onDelete?(id: number): Promise<any>
}

export function useDelete(config: DeleteConfig): MenuItem<DeleteConfig> {
    const [confirmDeleteModalOpen, setConfirmDeleteModalOpen] = useState(false)
    return [
        (props: ActionMenuProps, isTester: boolean, close: () => void, config: DeleteConfig) => {
            return {
                item: (
                    <DropdownItem
                        key="delete"
                        onClick={() => {
                            close()
                            setConfirmDeleteModalOpen(true)
                        }}
                        isDisabled={!isTester || !config.onDelete}
                    >
                        Delete
                    </DropdownItem>
                ),
                modal: (
                    <ConfirmDeleteModal
                        key="confirmDelete"
                        description={props.description}
                        isOpen={confirmDeleteModalOpen}
                        onClose={() => setConfirmDeleteModalOpen(false)}
                        onDelete={() => {
                            if (config.onDelete) {
                                return config.onDelete(props.id)
                            } else {
                                setConfirmDeleteModalOpen(false)
                                return Promise.resolve()
                            }
                        }}
                    />
                ),
            }
        },
        config,
    ]
}

export default function ActionMenu(props: ActionMenuProps) {
    const [menuOpen, setMenuOpen] = useState(false)
    const isTester = useTester(props.owner)

    const items = props.items.map(([provider, config]) => provider(props, isTester, () => setMenuOpen(false), config))
    return (
        <>
            <Dropdown
                menuAppendTo={() => document.body}
                toggle={<KebabToggle onToggle={() => setMenuOpen(!menuOpen)} />}
                isOpen={menuOpen}
                isPlain
                dropdownItems={items.map(mi => mi.item)}
            />
            {items.map(mi => mi.modal)}
        </>
    )
}
