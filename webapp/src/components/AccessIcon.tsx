import React from 'react';

import {
   LockedIcon,
} from '@patternfly/react-icons'

export default function AccessIcon({access}: { access: number | string}) {
   var color;
   var text;
   switch (access) {
   case 'PUBLIC':
   case 0: {
      color = "--pf-global--success-color--200"
      text = "Public"
      break;
   }
   case 'PROTECTED':
   case 1: {
      color = "--pf-global--warning-color--100"
      text = "Protected"
      break;
   }
   case 'PRIVATE':
   case 2: {
      color = "--pf-global--danger-color--100"
      text = "Private"
      break;
   }
   default: {
      color = "--pf-global--icon--Color--light"
      text = "Unknown"
      break;
   }
   }
   return (<>
      <LockedIcon style={{ fill: "var(" + color + ")" }} /><span>&nbsp;{text}</span>
   </>
   )
}
