import React from 'react';

import {
   LockedIcon,
} from '@patternfly/react-icons'

import {
   Radio,
} from '@patternfly/react-core';

type AccessChoiceProps = {
   checkedValue: number | string,
   onChange(access: number): void,
}

export default ({checkedValue, onChange}: AccessChoiceProps) => {
   return (<>
      <Radio id="access-0" name="PUBLIC"
             isChecked={checkedValue === 0 || checkedValue === "PUBLIC"}
             onChange={() => onChange(0)}
             label={ <><LockedIcon style={{ fill: "var(--pf-global--success-color--200)"}} /> Public</> } />
      <Radio id="access-1" name="PROTECTED"
             isChecked={checkedValue === 1 || checkedValue === "PROTECTED"}
             onChange={() => onChange(1)}
             label={ <><LockedIcon style={{ fill: "var(--pf-global--warning-color--100)"}} /> Protected</>} />
      <Radio id="access-2" name="PRIVATE"
             isChecked={checkedValue === 2 || checkedValue === "PRIVATE"}
             onChange={() => onChange(2)}
             label={ <><LockedIcon style={{ fill: "var(--pf-global--danger-color--100)"}} /> Private</>} />
   </>)
}