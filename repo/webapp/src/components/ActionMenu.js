import React, { useState, useEffect } from 'react'

import { useSelector } from 'react-redux'

import {
    Dropdown,
    DropdownItem,
    KebabToggle,
} from '@patternfly/react-core'

import { rolesSelector } from '../auth.js'

import ShareLinkModal from './ShareLinkModal'
import ChangeAccessModal from './ChangeAccessModal'

export default ({ id, owner, access, token, tokenToLink, extraItems, onTokenReset, onTokenDrop, onAccessUpdate }) => {
   const [menuOpen, setMenuOpen] = useState(false)

   const roles = useSelector(rolesSelector)

   const [shareLinkModalOpen, setShareLinkModalOpen] = useState(false)

   const [changeAccessModalOpen, setChangeAccessModalOpen] = useState(false)
   const [newAccess, setNewAccess] = useState(access)
   const [newOwner, setNewOwner] = useState(owner)

   useEffect(() => {
      setNewOwner(owner)
   }, [owner])

   const isOwner = roles && roles.includes(owner)

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
         <DropdownItem key="delete" isDisabled>Delete</DropdownItem>,
         ...(extraItems ? extraItems : [])
         ]}
      />
      <ShareLinkModal isOpen={ shareLinkModalOpen }
                      onClose={ () => setShareLinkModalOpen(false) }
                      isOwner={ isOwner }
                      link={ token ? tokenToLink(id, token) : null}
                      onReset={ () => onTokenReset(id) }
                      onDrop={ () => onTokenDrop(id) } />
      <ChangeAccessModal isOpen={ changeAccessModalOpen }
                         onClose={ () => setChangeAccessModalOpen(false) }
                         owner={ newOwner }
                         onOwnerChange={ setNewOwner }
                         access={ newAccess }
                         onAccessChange={ setNewAccess }
                         onUpdate={ () => {
                            setChangeAccessModalOpen(false)
                            onAccessUpdate(id, newOwner, newAccess)
                         }} />
   </>)
}