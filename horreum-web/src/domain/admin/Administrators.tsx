import {ReactElement, useState, useEffect, useContext} from "react"
import { useSelector } from "react-redux"
import { Button, DualListSelector, Form, FormGroup, Modal, Spinner, TextInput } from "@patternfly/react-core"

import { TabFunctionsRef } from "../../components/SavedTabs"
import {userApi, UserData} from "../../api"
import UserSearch from "../../components/UserSearch"
import { isAdminSelector, userName } from "../../auth"
import { noop } from "../../utils"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


function userElement(u: UserData) {
    return (
        <span data-user={u} key={u.username}>
            {userName(u)}
        </span>
    )
}

type AdministratorsProps = {
    funcsRef: TabFunctionsRef
}

export default function Administrators(props: AdministratorsProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [modified, setModified] = useState(false)
    const [resetCounter, setResetCounter] = useState(0)
    const [createNewUser, setCreateNewUser] = useState(false)
    const [availableUsers, setAvailableUsers] = useState<ReactElement[]>([])
    const [admins, setAdmins] = useState<ReactElement[]>([])
    const isAdmin = useSelector(isAdminSelector)
    useEffect(() => {
        if (isAdmin) {
            userApi.administrators().then(
                list => setAdmins(list.map(userElement)),
                error => alerting.dispatchError(error, "FETCH ADMINS", "Cannot fetch administrators")
            )
        }
    }, [isAdmin, resetCounter])
    props.funcsRef.current = {
        save: () =>
            userApi.updateAdministrators(
                admins.map(a => {
                    const user: UserData = a.props["data-user"]
                    return user.username
                })
            ).then(
                _ => setModified(false),
                error => alerting.dispatchError(error, "UPDATE ADMINS", "Cannot update administrators")
            ),
        reset: () => {
            setAvailableUsers([])
            setResetCounter(resetCounter + 1)
        },
        modified: () => modified,
    }
    return (
        <Form isHorizontal>
            <FormGroup label="Administrators" fieldId="administrators" onClick={e => e.preventDefault()}>
                <DualListSelector
                    availableOptions={availableUsers}
                    availableOptionsTitle="Available users"
                    availableOptionsActions={[
                        <UserSearch
                            key={0}
                            onUsers={users => {
                                setAvailableUsers(
                                    users.filter(u => !admins.some(m => m && m.key === u.username)).map(userElement)
                                )
                            }}
                        />,
                    ]}
                    chosenOptions={admins}
                    chosenOptionsTitle="Administrators"
                    onListChange={(newAvailable, newChosen) => {
                        setAvailableUsers(
                            (newAvailable as ReactElement[]).map(item => {
                                if (availableUsers.includes(item)) {
                                    return item
                                }
                                const user: any = item.props["data-user"]
                                // memberRoles.current.delete(user.username)
                                return userElement(user)
                            })
                        )
                        setAdmins(
                            (newChosen as ReactElement[]).map(item => {
                                if (admins.includes(item)) {
                                    return item
                                }
                                const user: any = item.props["data-user"]
                                // memberRoles.current.set(user.username, ["tester"])
                                return userElement(user)
                            })
                        )
                        setModified(true)
                    }}
                />
            </FormGroup>
            <FormGroup label="New user" fieldId="newuser">
                <Button onClick={() => setCreateNewUser(true)}>Create new user</Button>
            </FormGroup>
            <NewUserModal
                isOpen={createNewUser}
                onClose={() => setCreateNewUser(false)}
                onCreate={(user, password) => {
                    return userApi.createUser({ user, password }).then(
                        () => {
                            alerting.dispatchInfo(
                                "USER_CREATED",
                                "User created",
                                "User was successfully created",
                                3000
                            )
                        },
                        error => alerting.dispatchError(error, "USER_NOT_CREATED", "Failed to create new user.")
                    )
                }}
            />
        </Form>
    )
}

type NewUserModalProps = {
    isOpen: boolean
    onClose(): void
    onCreate(user: UserData, password: string): Promise<unknown>
}

function NewUserModal(props: NewUserModalProps) {
    const [username, setUsername] = useState<string>()
    const [password, setPassword] = useState<string>()
    const [email, setEmail] = useState<string>()
    const [firstName, setFirstName] = useState<string>()
    const [lastName, setLastName] = useState<string>()
    const [creating, setCreating] = useState(false)
    const valid = username && password && email && /^.+@.+\..+$/.test(email)
    useEffect(() => {
        setUsername(undefined)
        setPassword("")
        setEmail("")
        setFirstName("")
        setLastName("")
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
                            .onCreate({ id: "", username: username || "", email, firstName, lastName }, password || "")
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
                        helperText="This password is only temporary and the user will change it during first login."
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
                </Form>
            )}
        </Modal>
    )
}
