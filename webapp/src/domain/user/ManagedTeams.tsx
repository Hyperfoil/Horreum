import { ReactElement, useState, useEffect, useRef } from "react"
import { useDispatch, useSelector } from "react-redux"
import {
    Button,
    Checkbox,
    DualListSelector,
    Form,
    FormGroup,
    List,
    ListItem,
    Modal,
    Spinner,
    TextInput,
    TreeView,
} from "@patternfly/react-core"

import { TabFunctionsRef } from "../../components/SavedTabs"
import TeamSelect, { createTeam, Team } from "../../components/TeamSelect"
import { defaultTeamSelector, teamToName } from "../../auth"
import { alertAction, dispatchError, dispatchInfo } from "../../alerts"
import UserSearch from "../../components/UserSearch"
import { User, teamMembers, info, createUser, updateTeamMembers } from "./api"
import { noop } from "../../utils"

type ManagedTeamsProps = {
    funcs: TabFunctionsRef
    onModified(modified: boolean): void
}

function userName(user: User) {
    let str = ""
    if (user.firstName) {
        str += user.firstName + " "
    }
    if (user.lastName) {
        str += user.lastName + " "
    }
    if (user.firstName || user.lastName) {
        return str + " [" + user.username + "]"
    } else {
        return user.username
    }
}

type UserPermissionsProps = {
    user: User
    roles: string[]
    onRolesUpdate(roles: string[]): void
}

function getRoles(viewer: boolean, tester: boolean, uploader: boolean, manager: boolean) {
    const newRoles = []
    if (viewer) newRoles.push("viewer")
    if (tester) newRoles.push("tester")
    if (uploader) newRoles.push("uploader")
    if (manager) newRoles.push("manager")
    return newRoles
}

function UserPermissions({ user, roles, onRolesUpdate }: UserPermissionsProps) {
    const [viewer, setViewer] = useState(roles.includes("viewer"))
    const [tester, setTester] = useState(roles.includes("tester"))
    const [uploader, setUploader] = useState(roles.includes("uploader"))
    const [manager, setManager] = useState(roles.includes("manager"))
    return (
        <TreeView
            data-user={user}
            key={user.username}
            onCheck={(_, item) => {
                switch (item.id) {
                    case "viewer":
                        setViewer(!viewer)
                        onRolesUpdate(getRoles(!viewer, tester, uploader, manager))
                        break
                    case "tester":
                        setTester(!tester)
                        onRolesUpdate(getRoles(viewer, !tester, uploader, manager))
                        break
                    case "uploader":
                        setUploader(!uploader)
                        onRolesUpdate(getRoles(viewer, tester, !uploader, manager))
                        break
                    case "manager":
                        setManager(!manager)
                        onRolesUpdate(getRoles(viewer, tester, uploader, !manager))
                        break
                }
            }}
            data={[
                {
                    name: userName(user),
                    id: user.username,
                    children: [
                        { name: "Viewer", id: "viewer", hasCheck: true, checkProps: { checked: viewer } },
                        { name: "Tester", id: "tester", hasCheck: true, checkProps: { checked: tester } },
                        { name: "Uploader", id: "uploader", hasCheck: true, checkProps: { checked: uploader } },
                        { name: "Manager", id: "manager", hasCheck: true, checkProps: { checked: manager } },
                    ],
                },
            ]}
        />
    )
}

function member(user: User, memberRoles: Map<string, string[]>, setModified: (_: boolean) => void) {
    return (
        <UserPermissions
            key={user.username}
            data-user={user}
            user={user}
            roles={memberRoles.get(user.username) || []}
            onRolesUpdate={roles => {
                memberRoles.set(user.username, roles)
                setModified(true)
            }}
        />
    )
}

export default function ManagedTeams(props: ManagedTeamsProps) {
    const defaultTeam = useSelector(defaultTeamSelector)
    const [team, setTeam] = useState<Team>(createTeam(defaultTeam))
    const [nextTeam, setNextTeam] = useState<Team>()
    const [availableUsers, setAvailableUsers] = useState<ReactElement[]>([])
    const [members, setMembers] = useState<ReactElement[]>([])
    const memberRoles = useRef<Map<string, string[]>>(new Map())
    const [modified, setModified] = useState(false)
    const [createNewUser, setCreateNewUser] = useState(false)
    const [resetCounter, setResetCounter] = useState(0)
    const dispatch = useDispatch()
    const onModify = () => {
        setModified(true)
        props.onModified(true)
    }

    useEffect(() => {
        teamMembers(team.key).then(
            userRolesMap => {
                memberRoles.current = new Map(Object.entries(userRolesMap))
                info(Object.keys(userRolesMap)).then(
                    (users: User[]) => {
                        const userMap = new Map()
                        users.forEach(u => userMap.set(u.username, u))
                        setMembers(
                            Object.keys(userRolesMap).map(username => {
                                let user: User = userMap.get(username)
                                if (!user) {
                                    user = {
                                        id: "",
                                        username,
                                    }
                                }
                                return member(user, memberRoles.current, setModified)
                            })
                        )
                    },
                    error => dispatch(alertAction("FETCH_USERS_INFO", "Failed to fetch details for users", error))
                )
            },
            error => dispatch(alertAction("FETCH_TEAM_MEMBERS", "Failed to fetch team members", error))
        )
    }, [team, resetCounter])
    props.funcs.current = {
        save: () => {
            console.log(memberRoles.current)
            return updateTeamMembers(team.key, memberRoles.current).then(() => setModified(false))
        },
        reset: () => {
            setAvailableUsers([])
            setMembers([])
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
                        onSelect={anotherTeam => {
                            if (modified) {
                                setNextTeam(anotherTeam)
                            } else {
                                setTeam(anotherTeam)
                            }
                        }}
                    />
                </FormGroup>
                <FormGroup label="Members" fieldId="members" onClick={e => e.preventDefault()}>
                    <DualListSelector
                        availableOptions={availableUsers}
                        availableOptionsTitle="Available users"
                        availableOptionsActions={[
                            <UserSearch
                                key={0}
                                onUsers={users => {
                                    setAvailableUsers(
                                        users
                                            .filter(u => !members.some(m => m && m.key === u.username))
                                            .map(u => (
                                                <span data-user={u} key={u.username}>
                                                    {userName(u)}
                                                </span>
                                            ))
                                    )
                                }}
                            />,
                        ]}
                        chosenOptions={members}
                        chosenOptionsTitle={"Members of " + teamToName(team.key)}
                        onListChange={(newAvailable, newChosen) => {
                            setAvailableUsers(
                                (newAvailable as ReactElement[]).map(item => {
                                    if (availableUsers.includes(item)) {
                                        return item
                                    }
                                    const user: any = item.props["data-user"]
                                    memberRoles.current.delete(user.username)
                                    return (
                                        <span key={user.username} data-user={user}>
                                            {userName(user)}
                                        </span>
                                    )
                                })
                            )
                            setMembers(
                                (newChosen as ReactElement[]).map(item => {
                                    if (members.includes(item)) {
                                        return item
                                    }
                                    const user: any = item.props["data-user"]
                                    memberRoles.current.set(user.username, ["tester"])
                                    return member(user, memberRoles.current, onModify)
                                })
                            )
                            onModify
                        }}
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
                isOpen={createNewUser}
                onClose={() => setCreateNewUser(false)}
                onCreate={(user, password, roles) => {
                    return createUser(user, password, team.key, roles).then(
                        () => {
                            memberRoles.current.set(user.username, roles)
                            setMembers([...members, member(user, memberRoles.current, onModify)])
                            dispatchInfo(
                                dispatch,
                                "USER_CREATED",
                                "User created",
                                "User was successfully created",
                                3000
                            )
                        },
                        error => dispatchError(dispatch, error, "USER_NOT_CREATED", "Failed to create new user.")
                    )
                }}
            />
        </>
    )
}

type NewUserModalProps = {
    isOpen: boolean
    onClose(): void
    onCreate(user: User, password: string, roles: string[]): Promise<unknown>
}

function NewUserModal(props: NewUserModalProps) {
    const [username, setUsername] = useState<string>()
    const [password, setPassword] = useState<string>()
    const [email, setEmail] = useState<string>()
    const [firstName, setFirstName] = useState<string>()
    const [lastName, setLastName] = useState<string>()
    const [creating, setCreating] = useState(false)
    const [viewer, setViewer] = useState(true)
    const [tester, setTester] = useState(true)
    const [uploader, setUploader] = useState(false)
    const [manager, setManager] = useState(false)
    const valid = username && password && email && /^.+@.+\..+$/.test(email)
    useEffect(() => {
        setUsername(undefined)
        setPassword("")
        setEmail("")
        setFirstName("")
        setLastName("")
        setViewer(true)
        setTester(true)
        setUploader(false)
        setManager(false)
    }, [props.isOpen])
    return (
        <Modal
            title="Create new user"
            isOpen={props.isOpen}
            onClose={props.onClose}
            actions={[
                <Button
                    isDisabled={!valid}
                    onClick={() => {
                        setCreating(true)
                        props
                            .onCreate(
                                { id: "", username: username || "", email, firstName, lastName },
                                password || "",
                                getRoles(viewer, tester, uploader, manager)
                            )
                            .catch(noop)
                            .finally(() => {
                                setCreating(false)
                                props.onClose()
                            })
                    }}
                >
                    Create
                </Button>,
                <Button variant="secondary" onClick={props.onClose}>
                    Cancel
                </Button>,
            ]}
        >
            {creating ? (
                <Spinner size="xl" />
            ) : (
                <Form isHorizontal>
                    <FormGroup isRequired label="Username" fieldId="username">
                        <TextInput
                            isRequired
                            value={username}
                            onChange={setUsername}
                            validated={username ? "default" : "error"}
                        />
                    </FormGroup>
                    <FormGroup
                        isRequired
                        label="Temporary password"
                        fieldId="password"
                        helperText="This password is only temporary and theuser will change it during first login."
                    >
                        <TextInput
                            isRequired
                            value={password}
                            onChange={setPassword}
                            validated={password ? "default" : "error"}
                        />
                    </FormGroup>
                    <FormGroup isRequired label="Email" fieldId="email">
                        <TextInput
                            isRequired
                            type="email"
                            value={email}
                            onChange={setEmail}
                            validated={email && /^.+@.+\..+$/.test(email) ? "default" : "error"}
                        />
                    </FormGroup>
                    <FormGroup label="First name" fieldId="firstName">
                        <TextInput value={firstName} onChange={setFirstName} />
                    </FormGroup>
                    <FormGroup label="Last name" fieldId="lastName">
                        <TextInput value={lastName} onChange={setLastName} />
                    </FormGroup>
                    <FormGroup label="Permissions" fieldId="permissions">
                        <List isPlain>
                            <ListItem>
                                <Checkbox id="viewer" isChecked={viewer} onChange={setViewer} label="Viewer" />
                            </ListItem>
                            <ListItem>
                                <Checkbox id="tester" isChecked={tester} onChange={setTester} label="Tester" />
                            </ListItem>
                            <ListItem>
                                <Checkbox id="uploader" isChecked={uploader} onChange={setUploader} label="Uploader" />
                            </ListItem>
                            <ListItem>
                                <Checkbox id="manager" isChecked={manager} onChange={setManager} label="Manager" />
                            </ListItem>
                        </List>
                    </FormGroup>
                </Form>
            )}
        </Modal>
    )
}
