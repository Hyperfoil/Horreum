import {useContext, useEffect, useRef, useState} from "react"
import { useSelector } from "react-redux"
import { Button, FormGroup, Modal, TextInput } from "@patternfly/react-core"
import { TabFunctionsRef } from "../../components/SavedTabs"
import SplitForm from "../../components/SplitForm"
import TeamMembers, { TeamMembersFunctions } from "../user/TeamMembers"
import NewUserModal from "../user/NewUserModal"
import { isAdminSelector } from "../../auth"
import {userApi} from "../../api"
import { noop } from "../../utils"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type Team = {
    id: number
    name: string
    exists: boolean
    deleted?: boolean
}

type TeamsProps = {
    funcs: TabFunctionsRef
}

export default function Teams(props: TeamsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [teams, setTeams] = useState<Team[]>([])
    const [selected, setSelected] = useState<Team>()
    const [loading, setLoading] = useState(false)
    const [deleted, setDeleted] = useState<string[]>([])
    const [resetCounter, setResetCounter] = useState(0)
    const [membersModified, setMembersModified] = useState(false)
    const [nextTeam, setNextTeam] = useState<Team>()
    const [newUserModalOpen, setNewUserModalOpen] = useState(false)
    const isAdmin = useSelector(isAdminSelector)
    const teamFuncsRef = useRef<TeamMembersFunctions>()
    useEffect(() => {
        if (!isAdmin) {
            return // happens during reload
        }
        setLoading(true)
        userApi.getAllTeams()
            .then(teams => {
                const loaded = teams.sort().map((t, i) => ({ id: i, name: t, exists: true }))
                setTeams(loaded)
                if (loaded.length > 0) {
                    setSelected(loaded[0])
                }
            })
            .catch(error => alerting.dispatchError( error, "FETCH ALL TEAMS", "Failed to fetch list of all teams."))
            .catch(noop)
            .finally(() => setLoading(false))
    }, [isAdmin, resetCounter])

    function update(update: Partial<Team>) {
        if (selected) {
            const updated = { ...selected, ...update }
            setSelected(updated)
            setTeams(teams.map(t => (t.id === selected?.id ? updated : t)))
        }
    }
    function createTeam(team: Team) {
        return userApi.addTeam(team.name)
            .then(() => {
                // no-need to re-render
                team.exists = true
                alerting.dispatchInfo( "CREATED TEAM", "Team " + team.name + " was created.", "", 3000)
            })
            .catch(error => alerting.dispatchError( error, "CREATE TEAM", "Cannot create team " + team.name))
    }
    function saveMembers() {
        if (!selected) {
            return Promise.resolve()
        }
        const withExistingTeam = selected.exists ? Promise.resolve() : createTeam(selected)
        return withExistingTeam.then(_ => {
            if (teamFuncsRef.current) {
                return teamFuncsRef.current.save().then(() => {
                    setMembersModified(false)
                    alerting.dispatchInfo(
                        "TEAM MEMBERS",
                        "Team members updated",
                        "All changes for team " + selected.name + " have been applied.",
                        3000
                    )
                })
            } else {
                return Promise.reject("This should not happen")
            }
        })
    }
    props.funcs.current = {
        save: () => {
            // remove teams that were actually created again
            const reallyDeleted = deleted.filter(t => !teams.some(t2 => t2.name === t))
            // do not re-create roles for re-created teams
            teams
                .filter(team => !team.exists && deleted.some(t => t === team.name))
                .forEach(team => {
                    team.exists = true
                })
            return saveMembers()
                .then(() =>
                    Promise.all([
                        ...teams.filter(team => !team.exists).map(team => createTeam(team)),
                        ...reallyDeleted.map(team =>
                            userApi.deleteTeam(team).then(
                                _ => alerting.dispatchInfo( "DELETED TEAM", "Team " + team + " was deleted", "", 3000),
                                error => alerting.dispatchError( error, "DELETE TEAM", "Cannot delete team " + team)
                            )
                        ),
                    ])
                )
                .then(() => setDeleted([]))
                .catch(noop)
        },
        reset: () => {
            setDeleted([])
            setSelected(undefined)
            setResetCounter(resetCounter + 1)
        },
        modified: () => membersModified || teams.some(t => !t.exists) || deleted.length > 0,
    }
    return (
        <>
            <SplitForm
                itemType="Team"
                newItem={id => ({ id, name: "", exists: false })}
                canAddItem={true}
                addItemText="Add new team"
                noItemTitle="There are no teams"
                noItemText="Horreum currently does not have any teams registered."
                canDelete={true}
                confirmDelete={team => team.exists}
                onDelete={team => {
                    if (team.exists) {
                        setDeleted([...deleted, team.name])
                    }
                    // see onSelected
                    team.deleted = true
                }}
                items={teams}
                onChange={setTeams}
                selected={selected}
                onSelected={team => {
                    // When the team is not created yet but it has some members set up we don't want
                    // to ask for persisting them when it's deleted & unselected
                    if (selected && (membersModified || !selected.exists) && (selected.exists || !selected.deleted)) {
                        setNextTeam(team)
                    } else {
                        setSelected(team)
                        setMembersModified(false)
                    }
                }}
                loading={loading}
            >
                {selected && (
                    <>
                        <FormGroup
                            label="Name"
                            fieldId="name"
                            helperText="Team name must end with '-team' suffix, e.g. 'engineers-team'"
                        >
                            <TextInput
                                id="name"
                                value={selected.name}
                                onChange={name => update({ name })}
                                isReadOnly={selected.exists}
                                validated={
                                    selected.name === "" ||
                                    !selected.name.toLowerCase().endsWith("-team") ||
                                    selected.name.toLowerCase().startsWith("horreum.")
                                        ? "error"
                                        : "default"
                                }
                            />
                        </FormGroup>
                        <FormGroup label="Members" fieldId="members" onClick={e => e.preventDefault()}>
                            <TeamMembers
                                team={selected.name}
                                loadMembers={selected.exists}
                                resetCounter={resetCounter}
                                funcs={teamFuncsRef}
                                onModified={() => setMembersModified(true)}
                            />
                        </FormGroup>
                        <FormGroup label="New user" fieldId="newUser">
                            <Button onClick={() => setNewUserModalOpen(true)}>Create new user</Button>
                            <NewUserModal
                                team={selected.name}
                                isOpen={newUserModalOpen}
                                onClose={() => setNewUserModalOpen(false)}
                                onCreate={(user, roles) => {
                                    if (teamFuncsRef.current) {
                                        teamFuncsRef.current.addMember(user, roles)
                                    }
                                }}
                            />
                        </FormGroup>
                        <Modal
                            isOpen={nextTeam !== undefined}
                            onClose={() => setNextTeam(undefined)}
                            title="Save membership changes"
                            actions={[
                                <Button
                                    key="save"
                                    onClick={() => {
                                        saveMembers().then(() => setSelected(nextTeam))
                                        setNextTeam(undefined)
                                    }}
                                >
                                    Save
                                </Button>,
                                <Button key="cancel" variant="secondary" onClick={() => setNextTeam(undefined)}>
                                    Cancel
                                </Button>,
                                <Button
                                    key="ignore"
                                    variant="secondary"
                                    onClick={() => {
                                        setSelected(nextTeam)
                                        setNextTeam(undefined)
                                        setMembersModified(false)
                                    }}
                                >
                                    Ignore
                                </Button>,
                            ]}
                        >
                            Do you want to save membership update for team {selected.name}?
                        </Modal>
                    </>
                )}
            </SplitForm>
        </>
    )
}
