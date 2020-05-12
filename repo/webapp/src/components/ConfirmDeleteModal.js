import React from 'react';

import {
    Button,
    Modal,
} from '@patternfly/react-core';

export default ({ isOpen, onClose, onDelete, description, extra = "" }) => {
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