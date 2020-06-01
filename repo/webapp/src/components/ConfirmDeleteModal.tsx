import React from 'react';

import {
    Button,
    Modal,
} from '@patternfly/react-core';

type ConfirmDeleteModalProps = {
   isOpen: boolean,
   onClose(): void,
   onDelete(): void,
   description: string,
   extra?: string
}

export default ({ isOpen, onClose, onDelete, description, extra }: ConfirmDeleteModalProps) => {
   return (<Modal isSmall title="Confirm deletion"
                  isOpen={isOpen}
                  onClose={onClose}>
      <div>Do you really want to delete { description }?</div>
      { extra }
      <br />
      <div>
         <Button variant="primary" onClick={ onDelete }>Delete</Button>
         <Button variant="secondary" onClick={ onClose }>Cancel</Button>
      </div>
   </Modal>)
}