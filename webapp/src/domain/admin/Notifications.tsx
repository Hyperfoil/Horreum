import { useState } from "react"
import { useDispatch } from "react-redux"

import { ActionGroup, Button, Form, FormGroup, TextInput } from "@patternfly/react-core"
import NotificationMethodSelect from "../../components/NotificationMethodSelect"
import { fetchApi } from "../../services/api/index"
import { dispatchInfo, dispatchError } from "../../alerts"

const base = "/api/notifications"
const fetchMethods = (method: string | undefined, data: string) =>
    fetchApi(
        `${base}/test?method=${method ? encodeURIComponent(method) : ""}&data=${encodeURIComponent(data)}`,
        null,
        "post"
    )

export default function Notifications() {
    const [method, setMethod] = useState<string>()
    const [data, setData] = useState<string>("")
    const dispatch = useDispatch()
    return (
        <Form isHorizontal>
            <FormGroup label="Method" fieldId="method">
                <NotificationMethodSelect isDisabled={false} method={method} onChange={setMethod} />
            </FormGroup>
            <FormGroup label="Data" fieldId="data">
                <TextInput value={data} onChange={setData} />
            </FormGroup>
            <ActionGroup>
                <Button
                    onClick={() =>
                        fetchMethods(method, data).then(
                            () =>
                                dispatchInfo(
                                    dispatch,
                                    "NOTIFICATION_TEST",
                                    "Notification test succeeded",
                                    "Please check if the notification worked.",
                                    3000
                                ),
                            error => dispatchError(dispatch, error, "NOTIFICATION_TEST", "Notification test failed")
                        )
                    }
                >
                    Test notifications
                </Button>
            </ActionGroup>
        </Form>
    )
}
