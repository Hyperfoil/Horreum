import {useContext, useEffect, useRef, useState} from "react"
import {useSelector} from "react-redux"
import {
    Button,
    ClipboardCopy,
    DataList,
    DataListAction,
    DataListCell,
    DataListItem,
    DataListItemCells,
    DataListItemRow,
    Form,
    FormGroup,
    Modal,
} from "@patternfly/react-core"

import {TabFunctionsRef} from "../../components/SavedTabs"
import TeamSelect, {createTeam, Team} from "../../components/TeamSelect"
import {defaultTeamSelector, managedTeamsSelector, userName} from "../../auth"
import {userApi, UserData} from "../../api";
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {TeamMembersFunctions} from "./TeamMembers";
import NewUserModal from "./NewUserModal";

type ManageMachineAccountsProps = {
    funcs: TabFunctionsRef
    onModified(modified: boolean): void
}

export default function ManageMachineAccounts(props: ManageMachineAccountsProps) {
    let defaultTeam = useSelector(defaultTeamSelector)
    const managedTeams = useSelector(managedTeamsSelector)
    if (!defaultTeam || !managedTeams.includes(defaultTeam)) {
        defaultTeam = managedTeams.length > 0 ? managedTeams[0] : undefined
    }
    const [team, setTeam] = useState<Team>(createTeam(defaultTeam))
    const [resetCounter, setResetCounter] = useState(0)
    const {alerting} = useContext(AppContext) as AppContextType;
    const [machineUsers, setMachineUsers] = useState<UserData[]>([])
    const [createNewAccount, setCreateNewAccount] = useState(false)

    const [currentUser, setCurrentUser] = useState<string>()
    const [newPassword, setNewPassword] = useState<string>()
    const teamMembersFuncs = useRef<TeamMembersFunctions>()

    useEffect(() => {
        setMachineUsers([])
        userApi.machineAccounts(team.key).then(
            users => setMachineUsers(users),
            error => alerting.dispatchError(error, "FETCH_TEAM_MEMBERS", "Failed to fetch details for users")
        )
    }, [team, resetCounter])

    props.funcs.current = {
        save: () => teamMembersFuncs.current?.save() || Promise.resolve(),
        reset: () => setResetCounter(resetCounter + 1)
    }
    return (
        <>
            <Form isHorizontal>
                <FormGroup label="Select team" fieldId="team">
                    <TeamSelect
                        includeGeneral={false}
                        selection={team}
                        teamsSelector={managedTeamsSelector}
                        onSelect={anotherTeam => setTeam(anotherTeam)}
                    />
                </FormGroup>
                <FormGroup label="Machine Accounts" fieldId="machines" onClick={e => e.preventDefault()}>
                    <DataList aria-label="Machine accounts" isCompact={true}>
                        {machineUsers.map((u, i) =>
                            <DataListItem key={"machine-account-" + i} aria-labelledby={"machine-account-" + i}>
                                <DataListItemRow>
                                    <DataListItemCells
                                        dataListCells={[
                                            <DataListCell key={"content" + i}>
                                                <span id={"machine-account-" + i}>{userName(u)}</span>
                                            </DataListCell>,
                                        ]}
                                    />
                                    <DataListAction key={"action" + i} aria-labelledby={"reset-password" + i}
                                                    aria-label={"reset-password-" + i} id={"reset-password" + i}>
                                        <Button
                                            variant="danger" size="sm" key={"reset-password" + i}
                                            onClick={() => {
                                                if (confirm("Are you sure you want to reset password of user " + userName(u) + "?")) {
                                                    userApi.resetPassword(team.key, u.username).then(
                                                        newPassword => {
                                                            setCurrentUser(userName(u))
                                                            setNewPassword(newPassword)
                                                        },
                                                        error => alerting.dispatchError(error, "RESET_PASSWORD", "Failed to reset password")
                                                    )
                                                }
                                            }}
                                        >
                                            Reset Password
                                        </Button>
                                    </DataListAction>
                                </DataListItemRow>
                            </DataListItem>
                        )}
                    </DataList>
                </FormGroup>
                <FormGroup label="New account" fieldId="newAccount">
                    <Button onClick={() => setCreateNewAccount(true)}>Create new machine account</Button>
                </FormGroup>
            </Form>

            <NewUserModal
                team={team.key}
                isOpen={createNewAccount}
                machineAccount={true}
                onClose={() => setCreateNewAccount(false)}
                onCreate={(user, roles) => {
                    setMachineUsers(machineUsers.concat(user));
                    teamMembersFuncs.current && teamMembersFuncs.current.addMember(user, roles)
                }}
            />
            <Modal
                isOpen={newPassword != undefined}
                title={"New password of " + currentUser}
                aria-label="New password"
                variant="small"
                onClose={() => {
                    setNewPassword(undefined)
                }}>
                <ClipboardCopy isReadOnly>{newPassword}</ClipboardCopy>
            </Modal>
        </>
    )
}
