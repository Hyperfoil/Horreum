import { useState } from "react"
import { useDispatch } from "react-redux"
import { Button, Flex, FlexItem, SearchInput } from "@patternfly/react-core"
import { ArrowRightIcon } from "@patternfly/react-icons"
import Api, { UserData } from "../api"
import { alertAction } from "../alerts"

type UserSearchProps = {
    onUsers(users: UserData[]): void
}
export default function UserSearch(props: UserSearchProps) {
    const [userSearch, setUserSearch] = useState<string>()
    const [userSearchTimer, setUserSearchTimer] = useState<number>()
    const dispatch = useDispatch()
    const fireSearch = (query: string) => {
        if (!query) {
            // do not query all users in the system
            return
        }
        Api.userServiceSearchUsers(query).then(
            users => props.onUsers(users),
            error => dispatch(alertAction("USER_LOOKUP", "User lookup failed", error))
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
