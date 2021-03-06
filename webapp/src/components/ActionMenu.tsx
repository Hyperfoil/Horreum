import React, { useState, useEffect, ReactElement } from 'react'

import { useSelector } from 'react-redux'

import {
    Dropdown,
    DropdownItem,
    KebabToggle,
} from '@patternfly/react-core'

import { rolesSelector, Access } from '../auth'

import ShareLinkModal from './ShareLinkModal'
import ChangeAccessModal from './ChangeAccessModal'
import ConfirmDeleteModal from './ConfirmDeleteModal'

interface MenuItemProvider<C> {
   (props: ActionMenuProps, isOwner: boolean, close: () => void, config: C): {
      item: ReactElement,
      modal: ReactElement,
   }
}

export type MenuItem<C> = [ MenuItemProvider<C>, C ]

export type ActionMenuProps = {
   id: number,
   owner: string,
   access: Access,
   description: string,
   items: MenuItem<any>[],
}

type ShareLinkConfig = {
   token?: string,
   tokenToLink(id: number, token: string): string,
   onTokenReset(id: number): void,
   onTokenDrop(id: number): void,
}

export function useShareLink(config: ShareLinkConfig): MenuItem<ShareLinkConfig> {
   const [shareLinkModalOpen, setShareLinkModalOpen] = useState(false)
   return [ (props: ActionMenuProps, isOwner: boolean, close: () => void, config: ShareLinkConfig) => {
      return {
         item:
            <DropdownItem
               key="link"
               isDisabled={ props.access === 0 || (!config.token && !isOwner)}
               onClick={() => {
                  close()
                  setShareLinkModalOpen(true)
               }}
            >
               Shareable link
            </DropdownItem>,
         modal:
            <ShareLinkModal
               key="link"
               isOpen={ shareLinkModalOpen }
               onClose={ () => setShareLinkModalOpen(false) }
               isOwner={ isOwner }
               link={ config.token ? config.tokenToLink(props.id, config.token) : ""}
               onReset={ () => config.onTokenReset(props.id) }
               onDrop={ () => config.onTokenDrop(props.id) }
            />
      }
   }, config ];
}

type ChangeAccessConfig = {
   onAccessUpdate(id: number, owner: string, access: Access): void,
}

function ChangeAccessProvider(props: ActionMenuProps, isOwner: boolean, close: () => void, config: ChangeAccessConfig) {
   const [changeAccessModalOpen, setChangeAccessModalOpen] = useState(false)
   const [newAccess, setNewAccess] = useState<Access>(0)
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
      item:
         <DropdownItem
            key="access"
            onClick={() => {
               close()
               setChangeAccessModalOpen(true)
            }}
            isDisabled={!isOwner}
         >
            Change access
         </DropdownItem>,
      modal:
         <ChangeAccessModal
            key="changeAccess"
            isOpen={ changeAccessModalOpen }
            onClose={ onChangeAccessClose }
            owner={ newOwner }
            onOwnerChange={ setNewOwner }
            access={ newAccess }
            onAccessChange={ setNewAccess }
            onUpdate={ () => {
            config.onAccessUpdate(props.id, newOwner, newAccess)
               onChangeAccessClose()
            }}
         />
   }
}

export function useChangeAccess(config: ChangeAccessConfig): MenuItem<ChangeAccessConfig> {
   return [ ChangeAccessProvider, config]
}

type DeleteConfig = {
   onDelete?(id: number): void,
}

export function useDelete(config: DeleteConfig): MenuItem<DeleteConfig> {
   const [confirmDeleteModalOpen, setConfirmDeleteModalOpen] = useState(false)
   return [ (props: ActionMenuProps, isOwner: boolean, close: () => void, config: DeleteConfig) => {
      return {
         item:
            <DropdownItem
               key="delete"
               onClick={() => {
                  close()
                  setConfirmDeleteModalOpen(true)
               }}
               isDisabled={!isOwner || !config.onDelete}
            >
               Delete
            </DropdownItem>,
         modal:
            <ConfirmDeleteModal
               key="confirmDelete"
               description={props.description}
               isOpen={confirmDeleteModalOpen}
               onClose={ () => setConfirmDeleteModalOpen(false) }
               onDelete={() => {
                     setConfirmDeleteModalOpen(false)
                     if (config.onDelete) {
                        config.onDelete(props.id)
                     }
               }}
            />
      }
   }, config]
}

export default function ActionMenu(props: ActionMenuProps) {
   const [menuOpen, setMenuOpen] = useState(false)
   const roles = useSelector(rolesSelector)

   const isOwner = roles && roles.includes(props.owner)

   const items = props.items.map(([ provider, config ]) => provider(props, isOwner, () => setMenuOpen(false), config))
   return (<>
      <Dropdown
                toggle={<KebabToggle onToggle={() => setMenuOpen(!menuOpen)} />}
                isOpen={ menuOpen }
                isPlain
                dropdownItems={ items.map(mi => mi.item) }
      />
      { items.map(mi => mi.modal) }
   </>)
}