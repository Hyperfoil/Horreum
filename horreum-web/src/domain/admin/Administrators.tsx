import {ReactElement, useState, useEffect, useContext} from "react"
import { useSelector } from "react-redux"
import {Button, Form, FormGroup} from '@patternfly/react-core';
import {DualListSelector} from '@patternfly/react-core/deprecated';

import { TabFunctionsRef } from "../../components/SavedTabs"
import {userApi, UserData} from "../../api"
import UserSearch from "../../components/UserSearch"
import {isAdminSelector, userName} from "../../auth"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";
import NewUserModal from "../user/NewUserModal";
import {noop} from "../../utils";


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
                    onListChange={(_event, newAvailable, newChosen) => {
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
                onCreate={noop}
            />
        </Form>
    )
}
