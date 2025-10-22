import {useEffect, useState} from "react"

import {notificationsApi} from "../api"
import { SimpleSelect } from "@patternfly/react-templates"

type NotificationMethodSelectProps = {
    isDisabled: boolean
    method: string | undefined
    onChange(method: string): void
}

export default function NotificationMethodSelect({isDisabled, method, onChange}: NotificationMethodSelectProps) {
    const [methods, setMethods] = useState<string[]>([])
    useEffect(() => {
        notificationsApi.methods().then(response => setMethods(response))
    }, [])
    return (
        <SimpleSelect
            initialOptions={methods.map(m => ({value: m, content: m, selected: m === method}))}
            onSelect={(_, item) => onChange(item as string)}
            selected={method}
            isDisabled={isDisabled}
            toggleWidth="100%"
        />
    )
}
