import {ReactElement, useState, useEffect, useContext} from "react"
import { noop } from "../../utils"
import {Divider} from '@patternfly/react-core';
import {DualListSelector} from '@patternfly/react-core/deprecated';
import {getSubscription, updateSubscription, userApi, UserData} from "../../api"
import { teamToName } from "../../utils"
import { TabFunctionsRef } from "../../components/SavedTabs"
import UserSearch from "../../components/UserSearch"
import {AppContext} from "../../context/AppContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import {AuthBridgeContext} from "../../context/AuthBridgeContext";
import {AuthContextType} from "../../context/@types/authContextTypes";

type SubscriptionsProps = {
    testId: number
    testOwner?: string
    onModified(modified: boolean): void
    funcsRef: TabFunctionsRef
}

function userElement(user: UserData): ReactElement<any> {
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

function teamElement(team: string): ReactElement<any> {
    return <span key={team}>{teamToName(team)}</span>
}

export default function Subscriptions(props: SubscriptionsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const { isTester: isTesterFunc } = useContext(AuthBridgeContext) as AuthContextType;

    const isTester = isTesterFunc(props.testOwner)
    const [availableUsers, setAvailableUsers] = useState<ReactElement<any>[]>([])
    const [watchingUsers, setWatchingUsers] = useState<ReactElement<any>[]>([])
    const [optoutUsers, setOptoutUsers] = useState<ReactElement<any>[]>([])

    const [availableTeams, setAvailableTeams] = useState<ReactElement<any>[]>([])
    const [watchingTeams, setWatchingTeams] = useState<ReactElement<any>[]>([])

    const [reloadCounter, setReloadCounter] = useState(0)
    const updateUsers = (users: UserData[]) =>
        setAvailableUsers(users.filter(u => !watchingUsers.some(w => w && w.key === u.username)).map(userElement))
    useEffect(() => {
        if (!isTester) {
            return
        }
        getSubscription(props.testId, alerting).then(watch => {
            if (watch.users.length > 0) {
                userApi.info(watch.users).then(
                    users => setWatchingUsers(users.map(userElement)),
                    error => alerting.dispatchError(error,"USER_INFO", "User info lookup failed, error")
                )
            } else {
                setWatchingUsers([])
            }
            if (watch.optout.length > 0) {
                userApi.info(watch.optout).then(
                    users => setOptoutUsers(users.map(userElement)),
                    error => alerting.dispatchError(error,"USER_INFO", "User info lookup failed, error")
                )
            } else {
                setOptoutUsers([])
            }
            setWatchingTeams(watch.teams.map(teamElement))
        }, noop)
        userApi.getTeams().then(
            // We will filter in the component to not recompute this on watchingTeams change
            teamRoles => setAvailableTeams(teamRoles.map(teamElement)),
            error => alerting.dispatchError(error,"TEAM_LOOKUP", "Team lookup failed")
        )
    }, [isTester, reloadCounter, props.testId])

    props.funcsRef.current = {
        save: () =>
                updateSubscription({
                    testId: props.testId,
                    users: watchingUsers.map(u => u.key as string),
                    optout: optoutUsers.map(u => u.key as string),
                    teams: watchingTeams.map(t => t.key as string),
                }, alerting),
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
                onListChange={(_event, newAvailable, newChosen) => {
                    setAvailableUsers(newAvailable as ReactElement<any>[])
                    setWatchingUsers(newChosen as ReactElement<any>[])
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
                onListChange={(_event, newAvailable, newChosen) => {
                    setAvailableUsers(newAvailable as ReactElement<any>[])
                    setOptoutUsers(newChosen as ReactElement<any>[])
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
                onListChange={(_event, newAvailable, newChosen) => {
                    setAvailableTeams(newAvailable as ReactElement<any>[])
                    setWatchingTeams(newChosen as ReactElement<any>[])
                    props.onModified(true)
                }}
            />
        </>
    );
}
