import { useEffect, useState } from "react"

import { Select, SelectOption } from "@patternfly/react-core"
import Api from "../api"

type NotificationMethodSelectProps = {
    isDisabled: boolean
    method: string | undefined
    onChange(method: string): void
}

export default function NotificationMethodSelect({isDisabled, method, onChange}: NotificationMethodSelectProps) {
    const [methodOpen, setMethodOpen] = useState(false)
    const [methods, setMethods] = useState<string[]>([])
    useEffect(() => {
        Api.notificationServiceMethods().then(response => setMethods(response))
    }, [])
    return (
        <Select
            isDisabled={isDisabled}
            isOpen={methodOpen}
            onToggle={open => setMethodOpen(open)}
            selections={method}
            onSelect={(event, selection) => {
            onChange(selection.toString())
            setMethodOpen(false)
            }}
            placeholderText="Please select..."
        >
            {methods.map((m, i) => (
                <SelectOption key={i} value={m} />
            ))}
        </Select>
    )
}
