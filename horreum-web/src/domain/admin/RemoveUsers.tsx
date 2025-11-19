import UserSearch from "../../components/UserSearch";
import {useContext, useState} from "react";
import {UserData} from "../../generated";
import {
    Button,
    DataList,
    DataListAction,
    DataListCell,
    DataListItem,
    DataListItemCells,
    DataListItemRow,
    Form,
    FormGroup
} from "@patternfly/react-core";
import { userName } from "../../utils";
import {userApi} from "../../api";
import {AppContext} from "../../context/AppContext";
import {AppContextType} from "../../context/@types/appContextTypes";

export default function RemoveUsers() {
    const [users, setUsers] = useState<UserData[]>([])
    const {alerting} = useContext(AppContext) as AppContextType;

    return (
        <>
        <Form isHorizontal>
            <FormGroup label="Available users" fieldId="users">
                <UserSearch
                    key={0}
                    onUsers={users => {
                        setUsers(users)
                    }}
                />
            </FormGroup>
            <DataList aria-label="Users" isCompact={true}>
                {users.map((u, i) =>
                    <DataListItem key={"user-" + i} aria-labelledby={"user-" + i}>
                        <DataListItemRow>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key={"content" + i}>
                                        <span id={"user-" + i}>{userName(u)}</span>
                                    </DataListCell>,
                                ]}
                            />
                            <DataListAction key={"action" + i} aria-labelledby={"remove-user-" + i}
                                            aria-label={"remove-user-" + i} id={"remove-user-" + i}>
                                <Button
                                    variant="danger" size="sm" key={"remove-user" + i}
                                    onClick={() => {
                                        if (confirm("Are you sure you want to remove user " + userName(u) + "?")) {
                                            userApi.removeUser(u.username).then(
                                                _ => {
                                                    setUsers(users.filter(user => user != u));
                                                },
                                                error => alerting.dispatchError(error, "REMOVE_ACCOUNT", "Failed to remove account")
                                            )
                                        }
                                    }}
                                >
                                    Remove
                                </Button>
                            </DataListAction>
                        </DataListItemRow>
                    </DataListItem>
                )}
            </DataList>
        </Form>
</>
)
}
