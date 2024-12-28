import {Ref, useState} from "react"
import {State} from "../store"
import {useSelector} from "react-redux"

import {isAuthenticatedSelector, teamsSelector as allTeamsSelector, teamToName} from "../auth"
import {
    Divider,
    MenuToggle,
    MenuToggleElement,
    Select,
    SelectGroup,
    SelectList,
    SelectOption,
} from "@patternfly/react-core";

export interface Team {
    key: string
    toString: () => string
}

export function createTeam(role?: string) {
    switch (role) {
        case SHOW_ALL.key:
            return SHOW_ALL
        case ONLY_MY_OWN.key:
            return ONLY_MY_OWN
        default:
            return {key: role || "", toString: () => teamToName(role) || "No team"}
    }
}

export const ONLY_MY_OWN: Team = {key: "__my", toString: () => "Only my own"}
export const SHOW_ALL: Team = {key: "__all", toString: () => "Show all"}

type TeamSelectProps = {
    includeGeneral: boolean
    selection: string | Team
    teamsSelector?(state: State): string[]
    onSelect(selection: Team): void
}

export default function TeamSelect({includeGeneral, selection, teamsSelector, onSelect}: TeamSelectProps) {
    const [isOpen, setIsOpen] = useState(false);
    const teams = useSelector(teamsSelector || allTeamsSelector)

    const generalOptions = () =>
        <SelectList>
            {(useSelector(isAuthenticatedSelector) ? [SHOW_ALL, ONLY_MY_OWN] : [SHOW_ALL])
                .map(t => <SelectOption key={t.key} value={t.key}>{t.toString()}</SelectOption>)}
        </SelectList>

    const teamsAsSelectOptions = () =>
        <SelectList>
            {teams.map(t => <SelectOption key={t} value={t}>{teamToName(t)}</SelectOption>)}
        </SelectList>

    const toggle = (toggleRef: Ref<MenuToggleElement>) =>
        <MenuToggle
            ref={toggleRef}
            isFullWidth
            isExpanded={isOpen}
            onClick={() => setIsOpen(!isOpen)}
        >
            {selection.toString()}
        </MenuToggle>

    return (
        <Select
            id="team-select"
            aria-label="Select team"
            isOpen={isOpen}
            selected={selection.toString()}
            onSelect={(_, value) => {
                setIsOpen(false);
                onSelect(createTeam(value as string))
            }}
            onOpenChange={(isOpen) => setIsOpen(isOpen)}
            toggle={toggle}
            isScrollable
            maxMenuHeight="45vh"
            popperProps={{enableFlip: false, preventOverflow: true}}
            shouldFocusToggleOnSelect
        >
            {includeGeneral ?
                <>
                    <SelectGroup key="__general" label="General">
                        {generalOptions()}
                    </SelectGroup>
                    <Divider/>
                    <SelectGroup key="__role" label="Team">
                        {teamsAsSelectOptions()}
                    </SelectGroup>
                </>
                :
                teamsAsSelectOptions()}
        </Select>
    )
}
