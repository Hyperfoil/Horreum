import { Alert, FormGroup, Switch } from "@patternfly/react-core"
import { ConditionComponent as ConditionComponentDef } from "../api"
import LogSlider from "./LogSlider"
import EnumSelect from "./EnumSelect"
import NumberBound from "./NumberBound"

type ConditionComponentProps = {
    value: any
    onChange(value: any): void
    isTester: boolean
} & ConditionComponentDef

export default function ConditionComponent(props: ConditionComponentProps) {
    let component
    switch (props.type) {
        case "LOG_SLIDER":
            component = (
                <LogSlider
                    value={props.value * ((props.properties as any).scale || 1)}
                    onChange={value => {
                        const scale = (props.properties as any).scale
                        props.onChange(scale ? value / scale : value)
                    }}
                    isDisabled={!props.isTester}
                    isDiscrete={(props.properties as any).discrete}
                    min={(props.properties as any).min}
                    max={(props.properties as any).max}
                    unit={(props.properties as any).unit}
                />
            )
            break
        case "ENUM":
            component = (
                <EnumSelect
                    options={(props.properties as any).options}
                    selected={props.value}
                    onSelect={props.onChange}
                />
            )
            break
        case "NUMBER_BOUND":
            component = (
                <NumberBound
                    {...props.value}
                    onChange={(enabled: boolean, inclusive: boolean, value: number) => {
                        props.onChange({ enabled, inclusive, value })
                    }}
                />
            )
            break
        case "SWITCH":
            component = (
                <Switch
                    label="ON"
                    labelOff="OFF"
                    isChecked={props.value}
                    onChange={checked => props.onChange(checked)}
                />
            )
            break
        default:
            component = <Alert variant="danger" title={"Unsupported type " + props.type} />
            break
    }
    return (
        <FormGroup fieldId={props.name} key={props.name} label={props.title} helperText={props.description}>
            {component}
        </FormGroup>
    )
}
