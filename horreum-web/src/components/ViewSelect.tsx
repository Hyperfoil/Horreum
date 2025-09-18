import {View} from "../api"
import { SimpleSelect } from "./templates/SimpleSelect"

type ViewSelectProps = {
    views: View[]
    viewId: number | undefined
    onChange(viewId: number): void
}

export default function ViewSelect({views, viewId, onChange}: ViewSelectProps) {
    return (
        <SimpleSelect
            initialOptions={views.map(v => ({value: v.id, content: v.name, selected: v.id === viewId}))}
            onSelect={(_, item) => onChange(item as number)}
            selected={viewId}
        />
    )
}
