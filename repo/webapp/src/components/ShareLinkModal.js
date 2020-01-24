import React from 'react';

import {
    Button,
    ClipboardCopy,
    Modal,
    Text,
} from '@patternfly/react-core';

export default ({ isOpen, onClose, link, isOwner, onReset, onDrop }) => {
   return (
      <Modal isSmall title="Shareable link"
             isOpen={ isOpen }
             onClose={ onClose } >
      { link ? (<>
         <ClipboardCopy isReadOnly>{window.location.origin + link}</ClipboardCopy>
         { isOwner && <>
            <Button variant="secondary" onClick={ onReset }>Reset link</Button>
            <Button variant="secondary" onClick={ onDrop }>Drop link</Button>
         </>}
      </>) : (<>
         <Text component="p">No shareable link yet.</Text>
         <Button variant="primary" onClick={ onReset } >Create link</Button>
      </>)
      }
      </Modal>
   )
}