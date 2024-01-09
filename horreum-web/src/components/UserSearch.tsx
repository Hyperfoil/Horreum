import {useContext, useState} from "react"
import { Button, Flex, FlexItem, SearchInput } from "@patternfly/react-core"
import { ArrowRightIcon } from "@patternfly/react-icons"
import {userApi, UserData} from "../api"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";

type UserSearchProps = {
    onUsers(users: UserData[]): void
}
export default function UserSearch(props: UserSearchProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [userSearch, setUserSearch] = useState<string>()
    const [userSearchTimer, setUserSearchTimer] = useState<number>()
    const fireSearch = (query: string) => {
        if (!query) {
            // do not query all users in the system
            return
        }
        userApi.searchUsers(query).then(
            users => props.onUsers(users),
            error => alerting.dispatchError(error,"USER_LOOKUP", "User lookup failed")
        )
    }
    return (
        <Flex>
            <FlexItem>
                <SearchInput
                    style={{ width: "100%" }}
                    placeholder="Find user..."
                    value={userSearch}
                    onKeyDown={e => {
                        const value = (e.target as any)?.value
                        if (e.key === "Enter" && value) {
                            window.clearTimeout(userSearchTimer)
                            fireSearch(value)
                        }
                    }}
                    onChange={(event, value) => {
                        setUserSearch(value)
                        window.clearTimeout(userSearchTimer)
                        setUserSearchTimer(window.setTimeout(() => fireSearch(value), 1000))
                    }}
                    onClear={() => setUserSearch(undefined)}
                />
            </FlexItem>
            <FlexItem>
                <Button
                    variant="control"
                    onClick={() => {
                        window.clearTimeout(userSearchTimer)
                        setUserSearchTimer(undefined)
                        fireSearch(userSearch || "")
                    }}
                >
                    <ArrowRightIcon />
                </Button>
            </FlexItem>
        </Flex>
    )
}
