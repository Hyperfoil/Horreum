import React, { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { NavLink } from 'react-router-dom'

import { roleToName, userProfileSelector } from './auth'
import { fetchApi } from './services/api';
import { alertAction } from './alerts'

import {
    ActionGroup,
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
    Tabs,
    Tab,
    TextInput,
    Title,
} from '@patternfly/react-core'

import {
    UserIcon,
} from '@patternfly/react-icons'

import OwnerSelect from './components/OwnerSelect'
import SaveChangesModal from './components/SaveChangesModal'

const base = "/api/notifications"
const fetchMethods = () => fetchApi(`${base}/methods`, null, 'get')
const fetchSettings = (name: string, isTeam: boolean) => fetchApi(`${base}/settings?name=${name}&team=${isTeam}`, null, 'get')
const updateSettings = (name: string, isTeam: boolean, settings: Settings[]) => fetchApi(`${base}/settings?name=${name}&team=${isTeam}`, settings, 'post', {}, 'response')

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

type Settings = {
    id: number,
    method: string,
    data: string,
    disabled: boolean,
}

type SingleSettingsProps = {
    settings: Settings,
    methods: string[],
    onChange(): void,
}

const SingleSettings = ({ settings, methods, onChange } : SingleSettingsProps) => {
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

type SettingsListProps = {
    title: string,
    data?: Settings[],
    methods: string[],
    onUpdate(data: Settings[]): void,
    modified: boolean,
    saving: boolean,
    onSave(): void,
}

const SettingsList = ({ title, data, methods, onUpdate, modified, saving, onSave }: SettingsListProps) => {
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
                                <SingleSettings
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
            { modified &&
            <ActionGroup style={{ marginTop: "16px" }}>
                <Button
                    isDisabled={saving}
                    variant="primary"
                    onClick={onSave}
                >{ saving ? <>Saving <Spinner size="md"/></> : "Save" }</Button>
            </ActionGroup>
            }
        </>)
    } else {
        return <Bullseye><Spinner /></Bullseye>
    }
}

const EMPTY = { id: -1, method: "", data: "", disabled: false }

export function UserSettings() {
    const dispatch = useDispatch()
    const profile = useSelector(userProfileSelector)
    const [methods, setMethods] = useState<string[]>([])
    const [personal, setPersonal] = useState<Settings[]>()
    const [selectedTeam, setSelectedTeam] = useState<string>()
    const [team, setTeam] = useState<Settings[]>()
    const [saveFunction, setSaveFunction] = useState<() => Promise<void>>()
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
    const [activeTab, setActiveTab] = useState<number | string>(0)
    const [requestedTab, setRequestedTab] = useState<number | string>(0)
    const [modalOpen, setModalOpen] = useState(false)
    const [saving, setSaving] = useState(false)
    const reportError = (error: any) => {
        console.log(error)
        dispatch(alertAction("UPDATE_SETTINGS", "Failed to update settings", error))
        return Promise.reject()
    }
    const savePersonal = (): Promise<void> => {
        setSaving(true)
        return updateSettings(profile?.username || "user-should-be-set", false, personal || [])
            .then(_ => setSaveFunction(undefined), reportError).finally(() => setSaving(false))
    }
    const saveTeam = (): Promise<void> => {
        setSaving(true)
        return updateSettings(selectedTeam || "team-should-be-set", true, team || [])
            .then(_ => setSaveFunction(undefined), reportError).finally(() => setSaving(false))
    }
    return (
        <Card>
            <CardBody>
                <SaveChangesModal
                    isOpen={modalOpen}
                    onClose={ () => setModalOpen(false) }
                    onSave={ saveFunction && (() => saveFunction().then(_ => {
                                setActiveTab(requestedTab)
                            })) }
                    onReset={() => {
                        setActiveTab(requestedTab)
                        if (requestedTab === 0) {
                            setPersonal(undefined)
                            loadPersonal()
                        } else {
                            setTeam(undefined)
                            setSelectedTeam(undefined)
                        }
                    }}
                />
                <Tabs activeKey={activeTab} onSelect={(_, index) => {
                    if (!!saveFunction) {
                        setModalOpen(true)
                        setRequestedTab(index)
                    } else {
                        setActiveTab(index)}
                    }
                }>
                    <Tab key="personal-notifications" eventKey={0} title="Personal notifications">
                        <SettingsList
                            title="Personal notifications"
                            data={personal}
                            methods={methods}
                            onUpdate={ list => {
                                setPersonal(list)
                                setSaveFunction(() => savePersonal)
                            }}
                            modified={!!saveFunction}
                            saving={saving}
                            onSave={ savePersonal }/>
                    </Tab>
                    <Tab key="team-notifications" eventKey={1} title="Team notifications">
                        <Form isHorizontal={true} style={{ marginTop: "20px" }}>
                            <FormGroup label="Notification for team" fieldId="teamSelection">
                                <OwnerSelect
                                    includeGeneral={false}
                                    selection={ roleToName(selectedTeam) || ""}
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
                            <SettingsList
                                title="Team notifications"
                                data={team}
                                methods={methods}
                                onUpdate={ list => {
                                    setTeam(list)
                                    setSaveFunction(() => saveTeam)
                                }}
                                modified={!!saveFunction}
                                saving={saving}
                                onSave={saveTeam}/>
                        }
                        { !selectedTeam && <EmptyState><Title headingLevel="h3">No team selected</Title></EmptyState>}
                    </Tab>
                </Tabs>
            </CardBody>
        </Card>)
}