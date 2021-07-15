import React, { useState } from 'react';

import {
    Select,
    SelectGroup,
    SelectOption,
    SelectVariant,
    SelectOptionObject,
} from '@patternfly/react-core';

import { useSelector } from 'react-redux'

import { rolesSelector, roleToName } from '../auth'

export interface Role extends SelectOptionObject {
   key: string,
}

export const ONLY_MY_OWN: Role = { key: "__my", toString: () => "Only my own"}
export const SHOW_ALL: Role = { key: "__all", toString: () => "Show all" }

type OwnerSelectProps = {
   includeGeneral: boolean,
   selection: string,
   onSelect(selection: Role): void
}

export default function OwnerSelect({ includeGeneral, selection, onSelect }: OwnerSelectProps) {
   const roles = useSelector(rolesSelector)
   const rolesAsSelectOptions = () => {
      return (roles ? roles.filter(role => role.endsWith("-team"))
                           .sort()
                           .map(role => (
                     <SelectOption key={role} value={{ key: role, toString: () => roleToName(role) || "" } as Role}/>
                   )) : [])
   }
   const [expanded, setExpanded] = useState(false)
   return (
      <Select
         variant={SelectVariant.single}
         aria-label="Select ownership role"
         placeholderText="Select team..."
         onToggle={setExpanded}
         onSelect={(event, selection, isPlaceholder) => {
            setExpanded(false)
            onSelect(selection as Role)
         }}
         selections={selection}
         isOpen={expanded}
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