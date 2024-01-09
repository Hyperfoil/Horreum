import {ReactElement, useState, useEffect, useRef, MutableRefObject, useContext} from "react"
import { DualListSelector, TreeView } from "@patternfly/react-core"

import { teamToName, userName } from "../../auth"
import UserSearch from "../../components/UserSearch"
import {userApi, UserData} from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


type UserPermissionsProps = {
    user: UserData
    roles: string[]
    onRolesUpdate(roles: string[]): void
}

export function getRoles(viewer: boolean, tester: boolean, uploader: boolean, manager: boolean) {
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

function member(user: UserData, memberRoles: Map<string, string[]>, setModified: (_: boolean) => void) {
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

export type TeamMembersFunctions = {
    save(): Promise<any>
    addMember(user: UserData, roles: string[]): void
}

type TeamMembersProps = {
    team: string
    loadMembers?: boolean
    resetCounter?: number
    onModified(): void
    funcs: MutableRefObject<TeamMembersFunctions | undefined>
}

export default function TeamMembers(props: TeamMembersProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [availableUsers, setAvailableUsers] = useState<ReactElement[]>([])
    const [members, setMembers] = useState<ReactElement[]>([])
    const memberRoles = useRef<Map<string, string[]>>(new Map())
    useEffect(() => {
        setAvailableUsers([])
    }, [props.resetCounter])
    useEffect(() => {
        setMembers([])
        memberRoles.current.clear()
        // TODO loading spinner
        if (props.loadMembers === false) {
            return
        }
        userApi.teamMembers(props.team).then(
            userRolesMap => {
                memberRoles.current = new Map(Object.entries(userRolesMap))
                userApi.info(Object.keys(userRolesMap)).then(
                    users => {
                        const userMap = new Map()
                        users.forEach(u => userMap.set(u.username, u))
                        setMembers(
                            Object.keys(userRolesMap).map(username => {
                                let user = userMap.get(username)
                                if (!user) {
                                    user = {
                                        id: "",
                                        username,
                                    }
                                }
                                return member(user, memberRoles.current, props.onModified)
                            })
                        )
                    },
                    error => alerting.dispatchError(error, "FETCH_USERS_INFO", "Failed to fetch details for users")
                )
            },
            error => alerting.dispatchError(error, "FETCH_TEAM_MEMBERS", "Failed to fetch team members")
        )
    }, [props.team, props.resetCounter])
    props.funcs.current = {
        save: () => userApi.updateTeamMembers(props.team, Object.fromEntries(memberRoles.current)),
        addMember: (user: UserData, roles: string[]) => {
            memberRoles.current.set(user.username, roles)
            setMembers([...members, member(user, memberRoles.current, props.onModified)])
        },
    }
    return (
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
            chosenOptionsTitle={"Members of " + teamToName(props.team)}
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
                        return member(user, memberRoles.current, props.onModified)
                    })
                )
                props.onModified()
            }}
        />
    )
}
