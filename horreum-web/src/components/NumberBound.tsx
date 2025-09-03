import { useMemo } from "react"
import { Checkbox, Flex, FlexItem, Switch, TextInput } from "@patternfly/react-core"
import { v4 as uuidv4 } from "uuid"

type NumberBoundProps = {
    enabled: boolean
    inclusive: boolean
    value: number
    isDisabled?: boolean
    onChange(enabled: boolean, inclusive: boolean, value: number): void
}

export default function NumberBound({enabled, inclusive, value, isDisabled, onChange}: NumberBoundProps) {
    // without unique prefix the switch would emit wrong event
    const prefix = useMemo(() => uuidv4(), [])
    return (
        <Flex>
            <FlexItem>
                <Switch
                    id={prefix + "_enabled"}
                    isChecked={enabled}
                    isDisabled={isDisabled}
                    onChange={(_event, enabled) => {
                    onChange(enabled, inclusive, value)
                    }}
                    label="Enabled"
                />
            </FlexItem>
            <FlexItem>
                <TextInput
                    id={prefix + "_value"}
                    type="number"
                    isDisabled={!enabled || isDisabled}
                    onChange={(_event, value) => onChange(enabled, inclusive, Number.parseFloat(value))}
                    value={value}
                    onKeyDown={e => {
                        if (e.key === "Enter") e.preventDefault()
                    }}
                />
            </FlexItem>
            <FlexItem>
                <Checkbox
                    id={prefix + "_inclusive"}
                    isDisabled={!enabled || isDisabled}
                    isChecked={inclusive}
                    onChange={(_event, inclusive) => onChange(enabled, inclusive, value)}
                    label="Inclusive"
                />
            </FlexItem>
        </Flex>
    )
}
