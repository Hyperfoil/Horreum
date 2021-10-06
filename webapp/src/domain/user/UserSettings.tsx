import { useState, useEffect } from "react"
import { useDispatch, useSelector } from "react-redux"
import { NavLink } from "react-router-dom"

import { defaultTeamSelector, teamToName, userProfileSelector } from "../../auth"
import { fetchApi } from "../../services/api"
import { alertAction, dispatchInfo } from "../../alerts"
import SavedTabs, { SavedTab } from "../../components/SavedTabs"
import { updateDefaultRole, TryLoginAgain } from "../../auth"

import { Alert, Card, CardBody, EmptyState, Form, FormGroup, Title } from "@patternfly/react-core"

import { UserIcon } from "@patternfly/react-icons"

import TeamSelect, { createTeam, Team } from "../../components/TeamSelect"
import { NotificationSettingsList, NotificationConfig } from "./NotificationSettings"
import Profile from "./Profile"

const base = "/api/notifications"
const fetchMethods = () => fetchApi(`${base}/methods`, null, "get")
const fetchSettings = (name: string, isTeam: boolean) =>
    fetchApi(`${base}/settings?name=${name}&team=${isTeam}`, null, "get")
const updateSettings = (name: string, isTeam: boolean, settings: NotificationConfig[]) =>
    fetchApi(`${base}/settings?name=${name}&team=${isTeam}`, settings, "post", {}, "response")

export const UserProfileLink = () => {
    const profile = useSelector(userProfileSelector)
    if (profile) {
        return (
            <div style={{ margin: "10px" }}>
                <NavLink to="/usersettings">
                    <span style={{ color: "#d2d2d2" }}>
                        {profile.firstName}
                        {"\u00A0"}
                        {profile.lastName}
                        {"\u00A0"}
                    </span>
                    <UserIcon style={{ fill: "#d2d2d2" }} />
                </NavLink>
            </div>
        )
    } else return <></>
}

export function UserSettings() {
    const dispatch = useDispatch()
    const profile = useSelector(userProfileSelector)
    const prevDefaultTeam = useSelector(defaultTeamSelector)
    const [defaultTeam, setDefaultTeam] = useState<Team>(createTeam(prevDefaultTeam))
    useEffect(() => {
        setDefaultTeam(createTeam(prevDefaultTeam))
    }, [prevDefaultTeam])
    const [methods, setMethods] = useState<string[]>([])
    const [personal, setPersonal] = useState<NotificationConfig[]>()
    const [selectedTeam, setSelectedTeam] = useState<string>()
    const [team, setTeam] = useState<NotificationConfig[]>()
    const [modified, setModified] = useState(false)
    useEffect(() => {
        fetchMethods().then(response => setMethods(response))
    }, [])
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
        dispatch(alertAction("UPDATE_SETTINGS", "Failed to update settings", error))
        return Promise.reject()
    }
    return !profile ? (
        <Alert variant="warning" title="Anonymous access to user settings">
            <TryLoginAgain />
        </Alert>
    ) : (
        <Card>
            <CardBody>
                <SavedTabs
                    afterSave={() => {
                        setModified(false)
                        dispatchInfo(dispatch, "SAVE", "Saved!", "User settings succesfully updated!", 3000)
                    }}
                    afterReset={() => setModified(false)}
                >
                    <SavedTab
                        title="My profile"
                        fragment="profile"
                        onSave={() => updateDefaultRole(defaultTeam.key).catch(reportError)}
                        onReset={() => {
                            setDefaultTeam(createTeam(prevDefaultTeam))
                            setModified(false)
                        }}
                        isModified={() => modified}
                    >
                        <Profile
                            defaultRole={defaultTeam}
                            onDefaultRoleChange={role => {
                                setDefaultTeam(role)
                                setModified(true)
                            }}
                        />
                    </SavedTab>
                    <SavedTab
                        title="Personal notifications"
                        fragment="personal-notifications"
                        onSave={() => {
                            const username = profile?.username || "user-should-be-set"
                            return updateSettings(username, false, personal || []).catch(reportError)
                        }}
                        onReset={() => {
                            setPersonal(undefined)
                            loadPersonal()
                        }}
                        isModified={() => modified}
                    >
                        <NotificationSettingsList
                            title="Personal notifications"
                            data={personal}
                            methods={methods}
                            onUpdate={list => {
                                setPersonal(list)
                                setModified(true)
                            }}
                        />
                    </SavedTab>
                    <SavedTab
                        title="Team-notifications"
                        fragment="team-notifications"
                        onSave={() => {
                            const teamname = selectedTeam || "team-should-be-set"
                            return updateSettings(teamname, true, team || []).catch(reportError)
                        }}
                        onReset={() => {
                            setTeam(undefined)
                            setSelectedTeam(undefined)
                        }}
                        isModified={() => modified}
                    >
                        <Form isHorizontal={true} style={{ marginTop: "20px" }}>
                            <FormGroup label="Notification for team" fieldId="teamSelection">
                                <TeamSelect
                                    includeGeneral={false}
                                    selection={teamToName(selectedTeam) || ""}
                                    onSelect={role => {
                                        setTeam(undefined)
                                        setSelectedTeam(role.key)
                                        fetchSettings(role.key, true).then(
                                            response => setTeam(response || []),
                                            error =>
                                                dispatch(
                                                    alertAction(
                                                        "LOAD_SETTINGS",
                                                        "Failed to load notification settings",
                                                        error
                                                    )
                                                )
                                        )
                                    }}
                                />
                            </FormGroup>
                        </Form>
                        {selectedTeam && (
                            <NotificationSettingsList
                                title="Team notifications"
                                data={team}
                                methods={methods}
                                onUpdate={list => {
                                    setTeam(list)
                                    setModified(true)
                                }}
                            />
                        )}
                        {!selectedTeam && (
                            <EmptyState>
                                <Title headingLevel="h3">No team selected</Title>
                            </EmptyState>
                        )}
                    </SavedTab>
                </SavedTabs>
            </CardBody>
        </Card>
    )
}
