import React from 'react';

import {
   LockedIcon,
} from '@patternfly/react-icons'

import {
   Radio,
} from '@patternfly/react-core';
import { Access } from '../auth';

type AccessChoiceProps = {
   checkedValue: Access,
   onChange(access: Access): void,
}

export default function AccessChoice({checkedValue, onChange}: AccessChoiceProps) {
   return (<>
      <Radio id="access-0" name="PUBLIC"
             isChecked={checkedValue === 0}
             onChange={() => onChange(0)}
             label={ <><LockedIcon style={{ fill: "var(--pf-global--success-color--200)"}} /> Public</> } />
      <Radio id="access-1" name="PROTECTED"
             isChecked={checkedValue === 1}
             onChange={() => onChange(1)}
             label={ <><LockedIcon style={{ fill: "var(--pf-global--warning-color--100)"}} /> Protected</>} />
      <Radio id="access-2" name="PRIVATE"
             isChecked={checkedValue === 2}
             onChange={() => onChange(2)}
             label={ <><LockedIcon style={{ fill: "var(--pf-global--danger-color--100)"}} /> Private</>} />
   </>)
}
