import { useState } from "react"

import { Select, SelectGroup, SelectOption, SelectVariant, SelectOptionObject } from "@patternfly/react-core"

import { useSelector } from "react-redux"

import { isAuthenticatedSelector, teamsSelector, teamToName } from "../auth"

export interface Team extends SelectOptionObject {
    key: string
}

export function createTeam(role?: string) {
    return {
        key: role || "",
        toString: () => teamToName(role) || "No team",
    }
}

export const ONLY_MY_OWN: Team = { key: "__my", toString: () => "Only my own" }
export const SHOW_ALL: Team = { key: "__all", toString: () => "Show all" }

type TeamSelectProps = {
    includeGeneral: boolean
    selection: string | Team
    onSelect(selection: Team): void
}

export default function TeamSelect({ includeGeneral, selection, onSelect }: TeamSelectProps) {
    const teams = useSelector(teamsSelector)
    const isAuthenticated = useSelector(isAuthenticatedSelector)
    const generalOptions = [<SelectOption key="__all" value={SHOW_ALL} />]
    if (isAuthenticated) {
        generalOptions.push(<SelectOption key="__my" value={ONLY_MY_OWN} />)
    }
    const teamsAsSelectOptions = () => {
        return teams ? teams.map(team => <SelectOption key={team} value={createTeam(team)} />) : []
    }
    const [expanded, setExpanded] = useState(false)
    return (
        <Select
            variant={SelectVariant.single}
            aria-label="Select team"
            placeholderText="Select team..."
            onToggle={setExpanded}
            onSelect={(event, selection) => {
                setExpanded(false)
                onSelect(selection as Team)
            }}
            selections={selection}
            isOpen={expanded}
            isGrouped={includeGeneral}
        >
            {includeGeneral
                ? [
                      <SelectGroup key="__general" label="General" value="">
                          {generalOptions}
                      </SelectGroup>,
                      <SelectGroup key="__role" label="Team" value="">
                          {teamsAsSelectOptions()}
                      </SelectGroup>,
                  ]
                : teamsAsSelectOptions()}
        </Select>
    )
}
