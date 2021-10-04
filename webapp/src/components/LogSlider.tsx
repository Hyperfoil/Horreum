import { Flex, FlexItem, NumberInput, Slider } from "@patternfly/react-core"

type LogSliderProps = {
    value: number
    onChange(value: number): void
    isDisabled?: boolean
    isDiscrete?: boolean
    min?: number
    max?: number
    unit?: string
}

function toLog(value: number, min: number, max: number) {
    if (value < min) return 0
    return (100 * Math.log10(value / min)) / Math.log10(max / min)
}

function toPow(value: number, min: number, max: number) {
    if (value < 1) return 0
    return min * Math.pow(10, (value / 100) * Math.log10(max / min))
}

export default function LogSlider({
    value,
    isDisabled = false,
    isDiscrete = false,
    min = 1,
    max = 100,
    unit = "",
    ...props
}: LogSliderProps) {
    return (
        <Flex>
            <FlexItem grow={{ default: "grow" }}>
                <Slider
                    value={toLog(value, min, max)}
                    onChange={value => {
                        let x = toPow(value, min, max)
                        if (isDiscrete) {
                            x = Math.round(x)
                        }
                        props.onChange(x)
                    }}
                    areCustomStepsContinuous={true}
                    isDisabled={isDisabled}
                    inputLabel={unit}
                    customSteps={[
                        { value: 0, label: min + unit },
                        { value: 100, label: max + unit },
                    ]}
                />
            </FlexItem>
            <FlexItem alignSelf={{ default: "alignSelfFlexStart" }}>
                <NumberInput
                    value={Math.round((value + Number.EPSILON) * 100) / 100}
                    onChange={e => {
                        let x = Number.parseFloat((e.target as any).value)
                        if (isDiscrete) {
                            x = Math.round(x)
                        }
                        props.onChange(x)
                    }}
                    widthChars={5}
                    isDisabled={isDisabled}
                    min={min}
                    max={max}
                    onPlus={() => props.onChange(Math.min(value + min, max))}
                    onMinus={() => props.onChange(Math.max(value - min, min))}
                    unit={unit}
                />
            </FlexItem>
        </Flex>
    )
}
