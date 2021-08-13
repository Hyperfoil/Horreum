import React from 'react';

import {
    Button,
    Modal,
} from '@patternfly/react-core';

import { teamToName, Access } from '../auth'

import AccessChoice from './AccessChoice'
import TeamSelect from './TeamSelect'

type ChangeAccessModalProps = {
  isOpen: boolean,
  onClose(): void,
  owner: string,
  onOwnerChange(owner: string): void,
  access: Access,
  onAccessChange(access: Access): void,
  onUpdate(): void,
}

export default function ChangeAccessModal({ isOpen, onClose, owner, onOwnerChange, access, onAccessChange, onUpdate }: ChangeAccessModalProps) {
   return (
      <Modal variant="small"
             title="Change access rights"
             isOpen={ isOpen }
             onClose={ onClose } >
         <div>
            Owner:
            <TeamSelect includeGeneral={false}
                         selection={teamToName(owner) || ""}
                         onSelect={selection => onOwnerChange(selection.key) } />
         </div>
         <div>
            <AccessChoice checkedValue={access}
                          onChange={ onAccessChange } />
         </div>
         <div>
            <Button variant="primary" onClick={ onUpdate }>Update</Button>
            <Button variant="secondary" onClick={ onClose }>Cancel</Button>
         </div>
      </Modal>
   )
}