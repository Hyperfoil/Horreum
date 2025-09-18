import { SimpleSelect } from "./templates/SimpleSelect"

type TimeRangeSelectProps = {
    selection: TimeRange
    onSelect(range: TimeRange): void
    options: TimeRange[]
}

export type TimeRange = {
    from?: number
    to?: number
}

export default function TimeRangeSelect(props: TimeRangeSelectProps) {
    return (
        <SimpleSelect
            initialOptions={props.options.map((o, i) => ({value: i, content: o.toString(), selected: o === props.selection}))}
            onSelect={(_, item) => props.onSelect(props.options[item as number])}
            selected={props.selection}
        />
    )
}
