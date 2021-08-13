import { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { NavLink } from 'react-router-dom'

import {
    defaultTeamSelector,
    keycloakSelector,
    teamToName,
    userProfileSelector
} from './auth'
import { fetchApi } from './services/api';
import { alertAction, dispatchInfo } from './alerts'
import SavedTabs, { SavedTab } from './components/SavedTabs'
import { updateDefaultRole, TryLoginAgain } from './auth'

import {
    Alert,
    Bullseye,
    Button,
    Card,
    CardBody,
    DataList,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    DataListCell,
    DataListAction,
    EmptyState,
    Form,
    FormGroup,
    Select,
    SelectOption,
    Spinner,
    TextInput,
    Title,
} from '@patternfly/react-core'

import {
    UserIcon,
} from '@patternfly/react-icons'

import TeamSelect, { createTeam, Team } from './components/TeamSelect'

const base = "/api/notifications"
const fetchMethods = () => fetchApi(`${base}/methods`, null, 'get')
const fetchSettings = (name: string, isTeam: boolean) => fetchApi(`${base}/settings?name=${name}&team=${isTeam}`, null, 'get')
const updateSettings = (name: string, isTeam: boolean, settings: NotificationConfig[]) => fetchApi(`${base}/settings?name=${name}&team=${isTeam}`, settings, 'post', {}, 'response')

export const UserProfileLink = () => {
    const profile = useSelector(userProfileSelector)
    if (profile) {
    return (<div style={{ margin: "10px"}}>
        <NavLink to="/usersettings">
            <span style={{ color: "#d2d2d2" }}>{ profile.firstName }{ '\u00A0' }{ profile.lastName }{ '\u00A0' }</span>
            <UserIcon style={{ fill: "#d2d2d2" }} />
        </NavLink>
    </div>)
    } else return (<></>)
}

type NotificationConfig = {
    id: number,
    method: string,
    data: string,
    disabled: boolean,
}

type NotificationSettingsProps = {
    settings: NotificationConfig,
    methods: string[],
    onChange(): void,
}

const NotificationSettings = ({ settings, methods, onChange } : NotificationSettingsProps) => {
    const [methodOpen, setMethodOpen] = useState(false)
    return (
        <Form isHorizontal={true} style={{ marginTop: "20px", width: "100%" }}>
            <FormGroup label="Method" fieldId="method">
                <Select
                    isDisabled={ settings.disabled }
                    isOpen={ methodOpen }
                    onToggle={ open => setMethodOpen(open) }
                    selections={ settings.method }
                    onSelect={ (event, selection) => {
                        settings.method = selection.toString()
                        setMethodOpen(false)
                        onChange()
                    } }
                    placeholderText="Please select..."
                >{
                    methods.map((m, i) => <SelectOption key={i} value={m} />)
                }</Select>
            </FormGroup>
            <FormGroup label="Data" fieldId="data" helperText="e.g. email address, IRC channel...">
                <TextInput
                    isDisabled={ settings.disabled }
                    id="data"
                    value={ settings.data }
                    onChange={ value => {
                        settings.data = value
                        onChange()
                    }} />
            </FormGroup>
        </Form>
    )
}

type NotificationSettingsListProps = {
    title: string,
    data?: NotificationConfig[],
    methods: string[],
    onUpdate(data: NotificationConfig[]): void,
}

const NotificationSettingsList = ({ title, data, methods, onUpdate }: NotificationSettingsListProps) => {
    if (data) {
        return (<>
            <div style={{
                marginTop: "16px",
                marginBottom: "16px",
                width: "100%",
                display: "flex",
                justifyContent: "space-between",
            }} >
                <Title headingLevel="h3">{ title }</Title>
                <Button onClick={ () => onUpdate([...data, { ...EMPTY } ]) }>Add notification</Button>
            </div>
            <DataList aria-label="List of settings">
            { data.map((s, i) => (
                <DataListItem key={i} aria-labelledby="">
                    <DataListItemRow>
                        <DataListItemCells dataListCells={[
                            <DataListCell key="content">
                                <NotificationSettings
                                    settings={s}
                                    methods={methods}
                                    onChange={ () => onUpdate([...data]) } />
                            </DataListCell>
                        ]} />
                        <DataListAction
                            style={{
                                flexDirection: "column",
                                justifyContent: "center",
                            }}
                            id="delete"
                            aria-labelledby="delete"
                            aria-label="Settings actions"
                            isPlainButtonAction>
                            <Button
                                onClick={ () => {
                                    s.disabled = !s.disabled
                                    onUpdate([...data])
                                }}
                            >{ s.disabled ? "Enable" : "Disable" }</Button>
                            <Button
                                variant="secondary"
                                onClick={() => {
                                    data.splice(i, 1)
                                    onUpdate([...data])
                                }}
                            >Delete</Button>
                        </DataListAction>
                    </DataListItemRow>
                </DataListItem>
            ))}
            </DataList>
        </>)
    } else {
        return <Bullseye><Spinner /></Bullseye>
    }
}

type ProfileProps = {
    defaultRole: Team,
    onDefaultRoleChange(role: Team): void,
}

function Profile(props: ProfileProps) {
    const keycloak = useSelector(keycloakSelector)
    return (
        <Form isHorizontal={true} style={{ marginTop: "20px", width: "100%" }}>
            { keycloak && <FormGroup label="Account management" fieldId="account">
                <Button onClick={ () => {
                    window.location.href = keycloak.createAccountUrl({ redirectUri: window.location.href })
                }}>
                    Manage in Keycloak...
                </Button>
            </FormGroup> }
            <FormGroup label="Default team" fieldId="defaultRole">
                <TeamSelect
                    includeGeneral={ false }
                    selection={ props.defaultRole}
                    onSelect={ props.onDefaultRoleChange }/>
            </FormGroup>
        </Form>)
}


const EMPTY = { id: -1, method: "", data: "", disabled: false }

export function UserSettings() {
    const dispatch = useDispatch()
    const profile = useSelector(userProfileSelector)
    const prevDefaultTeam = useSelector(defaultTeamSelector)
    const [defaultTeam, setDefaultTeam] = useState<Team>(createTeam(prevDefaultTeam))
    useEffect(() => {
        setDefaultTeam(createTeam(prevDefaultTeam))
    }, [ prevDefaultTeam ])
    const [methods, setMethods] = useState<string[]>([])
    const [personal, setPersonal] = useState<NotificationConfig[]>()
    const [selectedTeam, setSelectedTeam] = useState<string>()
    const [team, setTeam] = useState<NotificationConfig[]>()
    const [modified, setModified] = useState(false)
    useEffect(() => {
        fetchMethods().then(response => setMethods(response))
    },[])
    const loadPersonal = () => {
        if (profile?.username) {
            fetchSettings(profile.username, false).then(
                response => setPersonal(response || []),
                error => dispatch(alertAction("LOAD_SETTINGS", "Failed to load notification settings", error))
            )
        }
    }
    useEffect(loadPersonal, [profile, dispatch])
    const reportError = (error: any) => {
        console.log(error)
        dispatch(alertAction("UPDATE_SETTINGS", "Failed to update settings", error))
        return Promise.reject()
    }
    return !profile ?
        ( <Alert
            variant="warning"
            title="Anonymous access to user settings">
            <TryLoginAgain />
        </Alert> ) :
        ( <Card>
            <CardBody>
                <SavedTabs
                    afterSave={ () => {
                        setModified(false)
                        dispatchInfo(dispatch, "SAVE", "Saved!", "User settings succesfully updated!", 3000);
                    } }
                    afterReset={ () => setModified(false) }
                >
                    <SavedTab
                        title="My profile"
                        fragment="profile"
                        onSave={ () => updateDefaultRole(defaultTeam.key).catch(reportError) }
                        onReset={ () => {
                            setDefaultTeam(createTeam(prevDefaultTeam))
                            setModified(false)
                        }}
                        isModified={ () => modified }
                    >
                        <Profile
                            defaultRole={ defaultTeam }
                            onDefaultRoleChange={ role => {
                                setDefaultTeam(role)
                                setModified(true)
                            }}
                        />
                    </SavedTab>
                    <SavedTab
                        title="Personal notifications"
                        fragment="personal-notifications"
                        onSave={ () => {
                            const username = profile?.username || "user-should-be-set"
                            return updateSettings(username, false, personal || []).catch(reportError)
                        }}
                        onReset={ () => {
                            setPersonal(undefined)
                            loadPersonal()
                        }}
                        isModified={ () => modified }
                    >
                        <NotificationSettingsList
                            title="Personal notifications"
                            data={personal}
                            methods={methods}
                            onUpdate={ list => {
                                setPersonal(list)
                                setModified(true)
                            }}
                        />
                    </SavedTab>
                    <SavedTab
                        title="Team-notifications"
                        fragment="team-notifications"
                        onSave={ () => {
                            const teamname = selectedTeam || "team-should-be-set"
                            return updateSettings(teamname, true, team || []).catch(reportError)
                        }}
                        onReset={ () => {
                            setTeam(undefined)
                            setSelectedTeam(undefined)
                        }}
                        isModified={ () => modified }
                    >
                        <Form isHorizontal={true} style={{ marginTop: "20px" }}>
                            <FormGroup label="Notification for team" fieldId="teamSelection">
                                <TeamSelect
                                    includeGeneral={false}
                                    selection={ teamToName(selectedTeam) || ""}
                                    onSelect={role => {
                                        setTeam(undefined)
                                        setSelectedTeam(role.key)
                                        fetchSettings(role.key, true).then(
                                            response => setTeam(response || []),
                                            error => dispatch(alertAction("LOAD_SETTINGS", "Failed to load notification settings", error))
                                        )
                                    } }
                                />
                            </FormGroup>
                        </Form>
                        { selectedTeam &&
                            <NotificationSettingsList
                                title="Team notifications"
                                data={team}
                                methods={methods}
                                onUpdate={ list => {
                                    setTeam(list)
                                    setModified(true)
                                }}
                            />
                        }
                        { !selectedTeam && <EmptyState><Title headingLevel="h3">No team selected</Title></EmptyState>}
                    </SavedTab>
                </SavedTabs>
            </CardBody>
        </Card>)
}