import React from 'react';

import {
    Button,
    Modal,
} from '@patternfly/react-core';

import { roleToName } from '../auth'

import AccessChoice from './AccessChoice'
import OwnerSelect from './OwnerSelect'

type ChangeAccessModalProps = {
  isOpen: boolean,
  onClose(): void,
  owner: string,
  onOwnerChange(owner: string): void,
  access: number,
  onAccessChange(access: number): void,
  onUpdate(): void,
}

export default ({ isOpen, onClose, owner, onOwnerChange, access, onAccessChange, onUpdate }: ChangeAccessModalProps) => {
   return (
      <Modal isSmall title="Change access rights"
             isOpen={ isOpen }
             onClose={ onClose } >
         <div>
            Owner:
            <OwnerSelect includeGeneral={false}
                         selection={roleToName(owner) || ""}
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