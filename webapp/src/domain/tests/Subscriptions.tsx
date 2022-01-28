import { ReactElement, useState, useEffect } from "react"
import { useDispatch } from "react-redux"
import { getSubscription, updateSubscription } from "./actions"
import { TestDispatch } from "./reducers"
import { noop } from "../../utils"
import { Divider, DualListSelector } from "@patternfly/react-core"
import { info, teams, User } from "../user/api"
import { alertAction } from "../../alerts"
import { teamToName, useTester } from "../../auth"
import { TabFunctionsRef } from "../../components/SavedTabs"
import UserSearch from "../../components/UserSearch"

type SubscriptionsProps = {
    testId: number
    testOwner?: string
    onModified(modified: boolean): void
    funcsRef: TabFunctionsRef
}

function userElement(user: User): ReactElement {
    let str = ""
    if (user.firstName) {
        str += user.firstName + " "
    }
    if (user.lastName) {
        str += user.lastName + " "
    }
    if (user.firstName || user.lastName) {
        return <span key={user.username}>{str + " [" + user.username + "]"}</span>
    } else {
        return <span key={user.username}>{user.username}</span>
    }
}

function teamElement(team: string): ReactElement {
    return <span key={team}>{teamToName(team)}</span>
}

export default function Subscriptions(props: SubscriptionsProps) {
    const dispatch = useDispatch<TestDispatch>()
    const isTester = useTester(props.testOwner)
    const [availableUsers, setAvailableUsers] = useState<ReactElement[]>([])
    const [watchingUsers, setWatchingUsers] = useState<ReactElement[]>([])
    const [optoutUsers, setOptoutUsers] = useState<ReactElement[]>([])

    const [availableTeams, setAvailableTeams] = useState<ReactElement[]>([])
    const [watchingTeams, setWatchingTeams] = useState<ReactElement[]>([])

    const [reloadCounter, setReloadCounter] = useState(0)
    const updateUsers = (users: User[]) =>
        setAvailableUsers(users.filter(u => !watchingUsers.some(w => w && w.key === u.username)).map(userElement))
    useEffect(() => {
        if (!isTester) {
            return
        }
        dispatch(getSubscription(props.testId)).then(watch => {
            if (watch.users.length > 0) {
                info(watch.users).then(
                    users => setWatchingUsers(users.map(userElement)),
                    error => dispatch(alertAction("USER_INFO", "User info lookup failed, error", error))
                )
            } else {
                setWatchingUsers([])
            }
            if (watch.optout.length > 0) {
                info(watch.optout).then(
                    users => setOptoutUsers(users.map(userElement)),
                    error => dispatch(alertAction("USER_INFO", "User info lookup failed, error", error))
                )
            } else {
                setOptoutUsers([])
            }
            setWatchingTeams(watch.teams.map(teamElement))
        }, noop)
        teams().then(
            // We will filter in the component to not recompute this on watchingTeams change
            teamRoles => setAvailableTeams(teamRoles.map(teamElement)),
            error => dispatch(alertAction("TEAM_LOOKUP", "Team lookup failed", error))
        )
    }, [isTester, reloadCounter, props.testId, dispatch])

    props.funcsRef.current = {
        save: () =>
            dispatch(
                updateSubscription({
                    testId: props.testId,
                    users: watchingUsers.map(u => u.key as string),
                    optout: optoutUsers.map(u => u.key as string),
                    teams: watchingTeams.map(t => t.key as string),
                })
            ),
        reset: () => setReloadCounter(reloadCounter + 1),
    }

    return (
        <>
            <DualListSelector
                availableOptions={availableUsers}
                availableOptionsTitle="Available users"
                availableOptionsActions={
                    isTester ? [<UserSearch key="usersearch" onUsers={users => updateUsers(users)} />] : []
                }
                chosenOptions={watchingUsers}
                chosenOptionsTitle="Watching users"
                onListChange={(newAvailable, newChosen) => {
                    setAvailableUsers(newAvailable as ReactElement[])
                    setWatchingUsers(newChosen as ReactElement[])
                    props.onModified(true)
                }}
            />
            <br />
            <Divider />
            <br />
            <DualListSelector
                availableOptions={availableUsers}
                availableOptionsTitle="Users to opt-out"
                availableOptionsActions={
                    isTester ? [<UserSearch key="usersearch" onUsers={users => updateUsers(users)} />] : []
                }
                chosenOptions={optoutUsers}
                chosenOptionsTitle="Opted out users"
                onListChange={(newAvailable, newChosen) => {
                    setAvailableUsers(newAvailable as ReactElement[])
                    setOptoutUsers(newChosen as ReactElement[])
                    props.onModified(true)
                }}
            />
            <br />
            <Divider />
            <br />
            <DualListSelector
                availableOptions={availableTeams.filter(t => !watchingTeams.some(wt => wt.key === t.key))}
                availableOptionsTitle="Available teams"
                chosenOptions={watchingTeams}
                chosenOptionsTitle="Watching teams"
                onListChange={(newAvailable, newChosen) => {
                    setAvailableTeams(newAvailable as ReactElement[])
                    setWatchingTeams(newChosen as ReactElement[])
                    props.onModified(true)
                }}
            />
        </>
    )
}
