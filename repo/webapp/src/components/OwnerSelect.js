import React, { useState } from 'react';

import {
    Select,
    SelectGroup,
    SelectOption,
    SelectVariant,
} from '@patternfly/react-core';

import { useSelector } from 'react-redux'

import { rolesSelector, roleToName } from '../auth.js'

export const ONLY_MY_OWN = { key: "__my", toString: () => "Only my own"}
export const SHOW_ALL = { key: "__all", toString: () => "Show all" }

export default ({ includeGeneral, selection, onSelect }) => {
   const roles = useSelector(rolesSelector)
   const rolesAsSelectOptions = () => {
      return (roles ? roles.filter(role => role.endsWith("-team"))
                           .sort()
                           .map(role => (
                     <SelectOption key={role} value={{ key: role, toString: () => roleToName(role) }}/>
                   )) : [])
   }
   const [expanded, setExpanded] = useState(false)
   return (
      <Select
         variant={SelectVariant.single}
         aria-label="Select ownership role"
         onToggle={setExpanded}
         onSelect={(event, selection, isPlaceholder) => {
            setExpanded(false)
            onSelect(selection)
         }}
         selections={selection}
         isExpanded={expanded}
         isGrouped={includeGeneral}
         >
         {  includeGeneral ? [
               <SelectGroup key="__general" label="General" value="">
                 <SelectOption value={ ONLY_MY_OWN } />
                 <SelectOption value={ SHOW_ALL } />
               </SelectGroup>,
               <SelectGroup key="__role" label="Run for team" value="">
                 { rolesAsSelectOptions() }
               </SelectGroup>
            ] : rolesAsSelectOptions()
         }
      </Select>
   )
}