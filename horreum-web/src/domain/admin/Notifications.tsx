import {useContext, useState} from "react"

import { ActionGroup, Button, Form, FormGroup, TextInput } from "@patternfly/react-core"
import NotificationMethodSelect from "../../components/NotificationMethodSelect"
import { notificationsApi } from "../../api"
import {AppContext} from "../../context/appContext";
import {AppContextType} from "../../context/@types/appContextTypes";


export default function Notifications() {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [method, setMethod] = useState<string>()
    const [data, setData] = useState<string>("")
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
                        notificationsApi.testNotifications(data, method).then(
                            () =>
                                alerting.dispatchInfo(
                                    "NOTIFICATION_TEST",
                                    "Notification test succeeded",
                                    "Please check if the notification worked.",
                                    3000
                                ),
                            error => alerting.dispatchError(error, "NOTIFICATION_TEST", "Notification test failed")
                        )
                    }
                >
                    Test notifications
                </Button>
            </ActionGroup>
        </Form>
    )
}
