import React, { useState, useEffect } from 'react'

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

type ActionMenuProps = {
   id: number,
   owner: string,
   access: Access,
   token?: string,
   tokenToLink(id: number, token: string): string,
   extraItems?: any[],
   onTokenReset(id: number): void,
   onTokenDrop(id: number): void,
   onAccessUpdate(id: number, owner: string, access: Access): void,
   description?: string,
   onDelete?(id: number): void,
}

export default function ActionMenu({ id, owner, access, token, tokenToLink, extraItems, onTokenReset, onTokenDrop, onAccessUpdate, description, onDelete }: ActionMenuProps) {
   const [menuOpen, setMenuOpen] = useState(false)

   const roles = useSelector(rolesSelector)

   const [shareLinkModalOpen, setShareLinkModalOpen] = useState(false)

   const [changeAccessModalOpen, setChangeAccessModalOpen] = useState(false)
   const [newAccess, setNewAccess] = useState(access)
   const [newOwner, setNewOwner] = useState(owner)

   const [confirmDeleteModalOpen, setConfirmDeleteModalOpen] = useState(false)

   useEffect(() => {
      setNewOwner(owner)
   }, [owner])
   useEffect(() => {
      setNewAccess(access)
   }, [access])

   const isOwner = roles && roles.includes(owner)

   const onChangeAccessClose = () => {
      setNewOwner(owner)
      setNewAccess(access)
      setChangeAccessModalOpen(false)
   }

   return (<>
      <Dropdown
                toggle={<KebabToggle onToggle={() => setMenuOpen(!menuOpen)} />}
                isOpen={ menuOpen }
                isPlain
                dropdownItems={[
         <DropdownItem key="link"
                       isDisabled={ access === 0 || (!token && !isOwner)}
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
                       isDisabled={!isOwner || !onDelete}
         >Delete</DropdownItem>,
         ...(extraItems ? extraItems : [])
         ]}
      />
      <ShareLinkModal isOpen={ shareLinkModalOpen }
                      onClose={ () => setShareLinkModalOpen(false) }
                      isOwner={ isOwner }
                      link={ token ? tokenToLink(id, token) : ""}
                      onReset={ () => onTokenReset(id) }
                      onDrop={ () => onTokenDrop(id) } />
      <ChangeAccessModal isOpen={ changeAccessModalOpen }
                         onClose={ onChangeAccessClose }
                         owner={ newOwner }
                         onOwnerChange={ setNewOwner }
                         access={ newAccess }
                         onAccessChange={ setNewAccess }
                         onUpdate={ () => {
                            onAccessUpdate(id, newOwner, newAccess)
                            onChangeAccessClose()
                         }} />
      <ConfirmDeleteModal isOpen={ confirmDeleteModalOpen }
                          onClose={ () => setConfirmDeleteModalOpen(false) }
                          onDelete={ () => {
                              setConfirmDeleteModalOpen(false)
                              if (onDelete) {
                                 onDelete(id)
                              }
                          }}
                          description={ description || "" } />
   </>)
}