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

export default function ConfirmDeleteModal({ isOpen, onClose, onDelete, description, extra }: ConfirmDeleteModalProps) {
   return (<Modal variant="small"
                  title="Confirm deletion"
                  isOpen={isOpen}
                  onClose={onClose}>
      <div>Do you really want to delete { description }?</div>
      { extra }
      <br />
      <div>
         <Button variant="danger" onClick={ onDelete }>Delete</Button>
         <Button variant="secondary" onClick={ onClose }>Cancel</Button>
      </div>
   </Modal>)
}