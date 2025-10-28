import {useState, useEffect, useRef, useContext} from "react"
import { NavLink } from "react-router-dom"

import { teamToName } from "../../utils"
import SavedTabs, { SavedTab, TabFunctions } from "../../components/SavedTabs"
import {NotificationSettings, notificationsApi, userApi} from "../../api"

import {
    Bullseye,
    Card,
    CardBody,
    EmptyState,
    Form,
    FormGroup,
    PageSection,
    Spinner,
} from "@patternfly/react-core"

import { UserIcon } from "@patternfly/react-icons"

import TeamSelect, { createTeam, Team } from "../../components/TeamSelect"
import { NotificationSettingsList } from "./NotificationSettings"
import Profile from "./Profile"
import ManagedTeams from "./ManagedTeams"
import {AppContext} from "../../context/AppContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import ApiKeys from "./ApiKeys";
import {FragmentTab} from "../../components/FragmentTabs";
import {AuthBridgeContext} from "../../context/AuthBridgeContext";
import {AuthContextType} from "../../context/@types/authContextTypes";

export const UserProfileLink = () => {
    const { isAuthenticated, name } = useContext(AuthBridgeContext) as AuthContextType;

    if (isAuthenticated && name) {
        return (
            <div style={{ margin: "10px" }}>
                <NavLink to="/usersettings">
                    <span style={{ color: "var(--pf-t--global--icon--color--regular)" }}>
                        {name}
                        <UserIcon style={{ paddingLeft: "4px" }} />
                    </span>
                </NavLink>
            </div>
        )
    } else return <></>
}

export function UserSettings() {
    const { username } = useContext(AuthBridgeContext) as AuthContextType;

    document.title = "User settings | Horreum"

    const { alerting } = useContext(AppContext) as AppContextType;
    const { managedTeams, defaultTeam: prevDefaultTeam } = useContext(AuthBridgeContext) as AuthContextType;

    const [defaultTeam, setDefaultTeam] = useState<Team>(createTeam(prevDefaultTeam))
    useEffect(() => {
        setDefaultTeam(createTeam(prevDefaultTeam))
    }, [prevDefaultTeam])
    const [personal, setPersonal] = useState<NotificationSettings[]>()
    const [selectedTeam, setSelectedTeam] = useState<string>()
    const [team, setTeam] = useState<NotificationSettings[]>()
    const [modified, setModified] = useState(false)
    const loadPersonal = () => {
        if (username) {
            notificationsApi.settings(username, false).then(
                response => setPersonal(response),
                error => alerting.dispatchError("LOAD_SETTINGS", "Failed to load notification settings", error)
            )
        }
    }
    useEffect(loadPersonal, [username])
    const teamFuncsRef = useRef<TabFunctions | undefined>(undefined)
    function reportError(error: any) {
        return alerting.dispatchError(error, "UPDATE_SETTINGS", "Failed to update user settings")
    }

    function updateDefaultTeam(team: string) {
        return userApi.setDefaultTeam(team)
            .then(
                _ => alerting.dispatchInfo("SAVE", "Saved!", "User Settings were successfully updated!", 3000),
                error => alerting.dispatchError(error, "SET_DEFAULT_TEAM", "Failed to update default team.")
            )
    }

    if (!username) {
        return (
            <Bullseye>
                <Spinner size="xl" />
                {"\u00A0"}Loading user profile...
            </Bullseye>
        )
    }
    return (
        <PageSection>
            <Card>
                <CardBody>
                    <SavedTabs
                        afterSave={() => {
                            setModified(false)
                            alerting.dispatchInfo("SAVE", "Saved!", "User settings successfully updated!", 3000)
                        }}
                        afterReset={() => setModified(false)}
                    >
                        <SavedTab
                            title="My profile"
                            fragment="profile"
                            canSave={true}
                            onSave={() => updateDefaultTeam(defaultTeam.key)}
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
                            canSave={true}
                            onSave={() => {
                                return notificationsApi.updateSettings(username || "user-should-be-set", false, personal || []).catch(
                                    reportError
                                )
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
                                onUpdate={list => {
                                    setPersonal(list)
                                    setModified(true)
                                }}
                            />
                        </SavedTab>
                        <SavedTab
                            title="Team notifications"
                            fragment="team-notifications"
                            canSave={true}
                            onSave={() => {
                                const teamname = selectedTeam || "team-should-be-set"
                                return notificationsApi.updateSettings(teamname, true, team || []).catch(
                                    reportError
                                )
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
                                            notificationsApi.settings(role.key, true).then(
                                                response => setTeam(response || []),
                                                error => alerting.dispatchError(
                                                            "LOAD_SETTINGS",
                                                            "Failed to load notification settings",
                                                            error
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
                                    onUpdate={list => {
                                        setTeam(list)
                                        setModified(true)
                                    }}
                                />
                            )}
                            {!selectedTeam && <EmptyState titleText="No team selected" headingLevel="h3" />}
                        </SavedTab>
                        <FragmentTab title="API keys" fragment="api-keys">
                            <ApiKeys/>
                        </FragmentTab>
                        {managedTeams.length > 0 ? (
                            <SavedTab
                                title="Managed teams"
                                fragment="managed-teams"
                                isModified={() => modified}
                                canSave={true}
                                onSave={() => teamFuncsRef.current?.save() || Promise.resolve()}
                                onReset={() => teamFuncsRef.current?.reset()}
                            >
                                <ManagedTeams funcs={teamFuncsRef} onModified={setModified} />
                            </SavedTab>
                        ) : (
                            <></>
                        )}
                    </SavedTabs>
                </CardBody>
            </Card>
        </PageSection>
    )
}
