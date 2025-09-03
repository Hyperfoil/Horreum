import { useState, useRef } from "react"
import { useSelector } from "react-redux"
import {Button, Form, FormGroup} from '@patternfly/react-core';
import {Modal} from '@patternfly/react-core/deprecated';

import { TabFunctionsRef } from "../../components/SavedTabs"
import TeamSelect, { createTeam, Team } from "../../components/TeamSelect"
import { defaultTeamSelector, managedTeamsSelector } from "../../auth"
import TeamMembers, { TeamMembersFunctions } from "./TeamMembers"
import NewUserModal from "./NewUserModal"
import { noop } from "../../utils"

type ManagedTeamsProps = {
    funcs: TabFunctionsRef
    onModified(modified: boolean): void
}

export default function ManagedTeams(props: ManagedTeamsProps) {
    let defaultTeam = useSelector(defaultTeamSelector)
    const managedTeams = useSelector(managedTeamsSelector)
    if (!defaultTeam || !managedTeams.includes(defaultTeam)) {
        defaultTeam = managedTeams.length > 0 ? managedTeams[0] : undefined
    }
    const [team, setTeam] = useState<Team>(createTeam(defaultTeam))
    const [nextTeam, setNextTeam] = useState<Team>()
    const [createNewUser, setCreateNewUser] = useState(false)
    const [modified, setModified] = useState(false)
    const [resetCounter, setResetCounter] = useState(0)
    const onModify = () => {
        setModified(true)
        props.onModified(true)
    }
    const teamMembersFuncs = useRef<TeamMembersFunctions>()

    props.funcs.current = {
        save: () => teamMembersFuncs.current?.save().then(_ => setModified(false)) || Promise.resolve(),
        reset: () => {
            setModified(false)
            setResetCounter(resetCounter + 1)
        },
    }
    return (
        <>
            <Form isHorizontal>
                <FormGroup label="Select team" fieldId="team">
                    <TeamSelect
                        includeGeneral={false}
                        selection={team}
                        teamsSelector={managedTeamsSelector}
                        onSelect={anotherTeam => (modified && setNextTeam(anotherTeam)) || setTeam(anotherTeam)}
                    />
                </FormGroup>
                <FormGroup label="Members" fieldId="members" onClick={e => e.preventDefault()}>
                    <TeamMembers
                        team={team.key}
                        onModified={onModify}
                        resetCounter={resetCounter}
                        funcs={teamMembersFuncs}
                    />
                </FormGroup>
                <FormGroup label="New user" fieldId="newuser">
                    <Button onClick={() => setCreateNewUser(true)}>Create new user</Button>
                </FormGroup>
            </Form>
            <Modal
                title="Save changes?"
                isOpen={nextTeam !== undefined}
                onClose={() => setNextTeam(undefined)}
                actions={[
                    <Button
                        onClick={() =>
                            props.funcs.current
                                ?.save()
                                .then(() => {
                                    setTeam(nextTeam as Team)
                                    setNextTeam(undefined)
                                })
                                .catch(noop)
                        }
                    >
                        Save
                    </Button>,
                    <Button variant="secondary" onClick={() => setNextTeam(undefined)}>
                        Cancel
                    </Button>,
                ]}
            >
                Current membership changes have not been saved. Save now?
            </Modal>
            <NewUserModal
                team={team.key}
                isOpen={createNewUser}
                onClose={() => setCreateNewUser(false)}
                onCreate={(user, _, roles) => {
                    teamMembersFuncs.current && teamMembersFuncs.current.addMember(user, roles)
                }}
            />
        </>
    )
}
