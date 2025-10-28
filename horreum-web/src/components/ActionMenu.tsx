import {useState, useEffect, ReactElement, ReactNode, useContext} from "react"

import { Access } from "../api"

import ChangeAccessModal from "./ChangeAccessModal"
import ConfirmDeleteModal from "./ConfirmDeleteModal"
import { Dropdown, DropdownItem, MenuToggle, MenuToggleElement } from "@patternfly/react-core"

import EllipsisVIcon from '@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon';
import {AuthBridgeContext} from "../context/AuthBridgeContext";
import {AuthContextType} from "../context/@types/authContextTypes";

interface MenuItemProvider<C> {
    (props: ActionMenuProps, isTester: boolean, close: () => void, config: C): {
        item: ReactElement<any>
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
    const { isTester } = useContext(AuthBridgeContext) as AuthContextType;
    const [menuOpen, setMenuOpen] = useState(false)
    const onSelect = () => {
        setMenuOpen(false);
    };

    const items = props.items.map(([provider, config]) => provider(props, isTester(), () => setMenuOpen(false), config))
    return (
        <>
            <Dropdown
                onSelect={onSelect}
                onOpenChange={(isOpen: boolean) => setMenuOpen(isOpen)}
                toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
                    <MenuToggle ref={toggleRef} onClick={() => setMenuOpen(!menuOpen)} isExpanded={menuOpen} variant="plain">
                        <EllipsisVIcon />
                    </MenuToggle>
                )}
                isOpen={menuOpen}
                popperProps={{position: "right"}}
            >
                {items.map(mi => mi.item)}
            </Dropdown>
            {items.map(mi => mi.modal)}
        </>
    )
}
