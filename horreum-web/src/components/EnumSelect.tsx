import { ReactNode, useState } from "react"

import {
	Select,
	SelectOption
} from '@patternfly/react-core/deprecated';

type EnumSelectProps = {
    options: Record<string, ReactNode>
    selected: string | undefined
    onSelect(option: string): void
    isDisabled?: boolean
}

export default function EnumSelect({options, selected, onSelect, isDisabled}: EnumSelectProps) {
    const [isOpen, setOpen] = useState(false)
    return (
        <Select
            isOpen={isOpen}
            onToggle={(_event, val) => setOpen(val)}
            placeholderText="Please select..."
            selections={selected}
            onSelect={(_, value) => {
            onSelect(value as string)
            setOpen(false)
            }}
            isDisabled={isDisabled}
        >
            {Object.entries(options).map(([name, title]) => (
                <SelectOption key={name} value={name}>
                    {title}
                </SelectOption>
            ))}
        </Select>
    )
}
