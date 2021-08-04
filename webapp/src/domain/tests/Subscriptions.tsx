import { ReactElement, useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
    Button,
    Divider,
    DualListSelector,
    SearchInput,
} from '@patternfly/react-core'
import {
    ArrowRightIcon
} from '@patternfly/react-icons'
import {
    info,
    search,
    teams,
    User,
} from '../user/api'
import {
    alertAction
} from '../../alerts'
import {
    roleToName,
    useTester,
} from '../../auth'
import { fetchApi } from '../../services/api';
import { TabFunctionsRef } from './Test'

type Watch = {
    id?: number,
    testId: number,
    users: string[],
    teams: string[],
}

function fetchWatch(testId: number) {
    return fetchApi(`/api/notifications/testwatch/${testId}`, null, 'get')
}

function updateWatch(watch: Watch) {
    return fetchApi(`/api/notifications/testwatch/${watch.testId}`, watch, 'post')
}

type SubscriptionsProps = {
    testId: number,
    testOwner?: string,
    onModified(modified: boolean): void,
    funcsRef: TabFunctionsRef,
}

function userElement(user: User): ReactElement {
    var str = "";
    if (user.firstName) {
        str += user.firstName + " "
    }
    if (user.lastName) {
        str += user.lastName + " "
    }
    if (user.firstName || user.lastName) {
        return <span key={user.username}>{ str + " [" + user.username + "]"}</span>
    } else {
        return <span key={user.username}>{ user.username }</span>
    }
}

function teamElement(team: string): ReactElement {
    return <span key={team}>{ roleToName(team) }</span>
}

export default function Subscriptions(props: SubscriptionsProps) {
    const dispatch = useDispatch()
    const isTester = useTester(props.testOwner)
    const [availableUsers, setAvailableUsers] = useState<ReactElement[]>([])
    const [watchingUsers, setWatchingUsers] = useState<ReactElement[]>([])
    const [userSearch, setUserSearch] = useState<string>()
    const [userSearchTimer, setUserSearchTimer] = useState<number>()

    const [availableTeams, setAvailableTeams] = useState<ReactElement[]>([])
    const [watchingTeams, setWatchingTeams] = useState<ReactElement[]>([])

    const [reloadCounter, setReloadCounter] = useState(0)
    const fireSearch = (query: string) => {
        search(query).then(
            (users: User[]) => setAvailableUsers(users
                .filter(u => !watchingUsers.some(w => w && w.key === u.username)).map(userElement)),
            error => dispatch(alertAction('USER_LOOKUP', "User lookup failed", error))
        )
    }
    useEffect(() => {
        if (!isTester) {
            return
        }
        fetchWatch(props.testId).then(
            watch => {
                if (watch.users.length > 0) {
                    info(watch.users).then(
                        users => setWatchingUsers(users.map(userElement)),
                        error => dispatch(alertAction("USER_INFO", "User info lookup failed, error", error))
                    )
                } else {
                    setWatchingUsers([])
                }
                setWatchingTeams(watch.teams.map(teamElement))
            },
            error => dispatch(alertAction("WATCH_LOOKUP", "Subscription lookup failed", error))
        )
        teams().then(
            // We will filter in the component to not recompute this on watchingTeams change
            teamRoles => setAvailableTeams(teamRoles.map(teamElement)),
            error => dispatch(alertAction("TEAM_LOOKUP", "Team lookup failed", error))
        )
    }, [isTester, reloadCounter])

    props.funcsRef.current = {
        save: () => updateWatch({
            testId: props.testId,
            users: watchingUsers.map(u => u.key as string),
            teams: watchingTeams.map(t => t.key as string),
        }),
        reset: () => setReloadCounter(reloadCounter + 1)
    }

    return (<>
        <DualListSelector
            availableOptions={ availableUsers }
            availableOptionsTitle="Available users"
            availableOptionsActions={isTester ? [
                <SearchInput
                    style={{ width: "100%"}}
                    placeholder="Find user..."
                    value={ userSearch }
                    onKeyDown={ e => {
                        const value = (e.target as any)?.value
                        if (e.key === "Enter" && value) {
                            window.clearTimeout(userSearchTimer)
                            fireSearch(value)
                        }
                    }}
                    onChange={ value => {
                        setUserSearch(value)
                        window.clearTimeout(userSearchTimer)
                        setUserSearchTimer(window.setTimeout(() => fireSearch(value), 1000))
                    }}
                    onClear={ () => setUserSearch(undefined) }
                />,
                <Button
                    variant="control"
                    onClick={() => {
                        window.clearTimeout(userSearchTimer)
                        setUserSearchTimer(undefined)
                        if (userSearch) {
                            fireSearch(userSearch)
                        }
                    }}
                ><ArrowRightIcon /></Button>
            ] : []}
            chosenOptions={ watchingUsers }
            chosenOptionsTitle="Watching users"
            onListChange={ (newAvailable, newChosen) => {
                setAvailableUsers((newAvailable as ReactElement[]))
                setWatchingUsers((newChosen as ReactElement[]))
                props.onModified(true)
            }}
        />
        <br /><Divider /><br />
        <DualListSelector
            availableOptions={ availableTeams.filter(t => !watchingTeams.some(wt => wt.key === t.key)) }
            availableOptionsTitle="Available teams"
            chosenOptions={ watchingTeams }
            chosenOptionsTitle="Watching teams"
            onListChange={ (newAvailable, newChosen) => {
                setAvailableTeams((newAvailable as ReactElement[]))
                setWatchingTeams((newChosen as ReactElement[]))
                props.onModified(true)
            }}
        />
    </>)
}