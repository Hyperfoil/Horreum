import { useMemo } from "react"
import { Checkbox, Flex, FlexItem, Switch, TextInput } from "@patternfly/react-core"
import { v4 as uuidv4 } from "uuid"

type NumberBoundProps = {
    enabled: boolean
    inclusive: boolean
    value: number
    onChange(enabled: boolean, inclusive: boolean, value: number): void
}

export default function NumberBound(props: NumberBoundProps) {
    // without unique prefix the switch would emit wrong event
    const prefix = useMemo(() => uuidv4(), [])
    return (
        <Flex>
            <FlexItem>
                <Switch
                    id={prefix + "_enabled"}
                    isChecked={props.enabled}
                    onChange={enabled => {
                        props.onChange(enabled, props.inclusive, props.value)
                    }}
                    label="Enabled"
                    labelOff="Disabled"
                />
            </FlexItem>
            <FlexItem>
                <TextInput
                    id={prefix + "_value"}
                    type="number"
                    isDisabled={!props.enabled}
                    onChange={value => props.onChange(props.enabled, props.inclusive, Number.parseFloat(value))}
                    value={props.value}
                    onKeyDown={e => {
                        if (e.key === "Enter") e.preventDefault()
                    }}
                />
            </FlexItem>
            <FlexItem>
                <Checkbox
                    id={prefix + "_inclusive"}
                    isDisabled={!props.enabled}
                    isChecked={props.inclusive}
                    onChange={inclusive => props.onChange(props.enabled, inclusive, props.value)}
                    label="Inclusive"
                />
            </FlexItem>
        </Flex>
    )
}
