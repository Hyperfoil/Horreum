import { useState } from "react"
import {
	Select,
	SelectOption
} from '@patternfly/react-core/deprecated';

import { View } from "../api"

type ViewSelectProps = {
    views: View[]
    viewId: number | undefined
    onChange(viewId: number): void
}

export default function ViewSelect({views, viewId, onChange}: ViewSelectProps) {
    const [isOpen, setOpen] = useState(false)
    const selected = views.find(v => v.id === viewId)
    return (
        <Select
            isOpen={isOpen}
            onToggle={(_event, val) => setOpen(val)}
            selections={selected !== undefined ? { ...selected, toString: () => selected.name } : undefined}
            onSelect={(_, item) => {
            onChange((item as View).id)
            setOpen(false)
            }}
        >
            {views.map(view => (
                <SelectOption key={view.id} value={view}>
                    {view.name}
                </SelectOption>
            ))}
        </Select>
    )
}
