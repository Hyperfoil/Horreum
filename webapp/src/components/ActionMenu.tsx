import React, { useState, useEffect, ReactElement } from 'react'

import { useSelector } from 'react-redux'

import {
    Dropdown,
    DropdownItem,
    DropdownItemProps,
    KebabToggle,
} from '@patternfly/react-core'

import { rolesSelector, Access } from '../auth'

import ShareLinkModal from './ShareLinkModal'
import ChangeAccessModal from './ChangeAccessModal'
import ConfirmDeleteModal from './ConfirmDeleteModal'

export type DropdownItemProvider = (closeFunc: () => void) => ReactElement<DropdownItemProps, any>

interface DeleteConfirmModal {
   (id: number, description: string, isOpen: boolean, onClose: () => void, onDelete: () => void): any
}

type ActionMenuProps = {
   id: number,
   owner: string,
   access: Access,
   token?: string,
   tokenToLink(id: number, token: string): string,
   extraItems?: DropdownItemProvider[],
   onTokenReset(id: number): void,
   onTokenDrop(id: number): void,
   onAccessUpdate(id: number, owner: string, access: Access): void,
   description: string,
   onDelete?(id: number): void,
   deleteConfirmModal?: DeleteConfirmModal,
}

export default function ActionMenu(props: ActionMenuProps) {
   const [menuOpen, setMenuOpen] = useState(false)

   const roles = useSelector(rolesSelector)

   const [shareLinkModalOpen, setShareLinkModalOpen] = useState(false)

   const [changeAccessModalOpen, setChangeAccessModalOpen] = useState(false)
   const [newAccess, setNewAccess] = useState(props.access)
   const [newOwner, setNewOwner] = useState(props.owner)

   const [confirmDeleteModalOpen, setConfirmDeleteModalOpen] = useState(false)

   useEffect(() => {
      setNewOwner(props.owner)
   }, [props.owner])
   useEffect(() => {
      setNewAccess(props.access)
   }, [props.access])

   const isOwner = roles && roles.includes(props.owner)

   const onChangeAccessClose = () => {
      setNewOwner(props.owner)
      setNewAccess(props.access)
      setChangeAccessModalOpen(false)
   }

   const deleteConfirmModal = props.deleteConfirmModal || ((id, description, isOpen, onClose, onDelete) => (
      <ConfirmDeleteModal isOpen={isOpen} onClose={onClose} onDelete={onDelete} description={description}/>)
   )

   return (<>
      <Dropdown
                toggle={<KebabToggle onToggle={() => setMenuOpen(!menuOpen)} />}
                isOpen={ menuOpen }
                isPlain
                dropdownItems={[
         <DropdownItem key="link"
                       isDisabled={ props.access === 0 || (!props.token && !isOwner)}
                       onClick={() => {
                           setMenuOpen(false)
                           setShareLinkModalOpen(true)
         }}>Shareable link</DropdownItem>,
         <DropdownItem key="access"
                       onClick={() => {
                           setMenuOpen(false)
                           setChangeAccessModalOpen(true)
                       }}
                       isDisabled={!isOwner}
         >Change access</DropdownItem>,
         <DropdownItem key="delete"
                       onClick={() => {
                           setMenuOpen(false)
                           setConfirmDeleteModalOpen(true)
                       }}
                       isDisabled={!isOwner || !props.onDelete}
         >Delete</DropdownItem>,
         ...(props.extraItems ? props.extraItems.map(item => item(() => setMenuOpen(false))) : [])
         ]}
      />
      <ShareLinkModal isOpen={ shareLinkModalOpen }
                      onClose={ () => setShareLinkModalOpen(false) }
                      isOwner={ isOwner }
                      link={ props.token ? props.tokenToLink(props.id, props.token) : ""}
                      onReset={ () => props.onTokenReset(props.id) }
                      onDrop={ () => props.onTokenDrop(props.id) } />
      <ChangeAccessModal isOpen={ changeAccessModalOpen }
                         onClose={ onChangeAccessClose }
                         owner={ newOwner }
                         onOwnerChange={ setNewOwner }
                         access={ newAccess }
                         onAccessChange={ setNewAccess }
                         onUpdate={ () => {
                           props.onAccessUpdate(props.id, newOwner, newAccess)
                            onChangeAccessClose()
                         }} />
      { deleteConfirmModal(props.id,
                           props.description,
                           confirmDeleteModalOpen, () => setConfirmDeleteModalOpen(false),
                           () => {
                              setConfirmDeleteModalOpen(false)
                              if (props.onDelete) {
                                 props.onDelete(props.id)
                              }
                           }) }

   </>)
}