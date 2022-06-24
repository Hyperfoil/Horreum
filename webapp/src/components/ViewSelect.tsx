import { useState } from "react"
import { Select, SelectOption } from "@patternfly/react-core"

import { View } from "../api"

type ViewSelectProps = {
    views: View[]
    viewId: number | undefined
    onChange(viewId: number): void
}

export default function ViewSelect(props: ViewSelectProps) {
    const [isOpen, setOpen] = useState(false)
    const selected = props.views.find(v => v.id === props.viewId)
    return (
        <Select
            isOpen={isOpen}
            onToggle={setOpen}
            selections={selected !== undefined ? { ...selected, toString: () => selected.name } : undefined}
            onSelect={(_, item) => {
                props.onChange((item as View).id)
                setOpen(false)
            }}
        >
            {props.views.map(view => (
                <SelectOption key={view.id} value={view}>
                    {view.name}
                </SelectOption>
            ))}
        </Select>
    )
}
