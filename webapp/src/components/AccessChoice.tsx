import { Radio } from "@patternfly/react-core"
import AccessIcon from "./AccessIcon"
import { Access } from "../auth"

type AccessChoiceProps = {
    checkedValue: Access
    onChange(access: Access): void
}

export default function AccessChoice({ checkedValue, onChange }: AccessChoiceProps) {
    return (
        <>
            <Radio
                id="access-0"
                name="PUBLIC"
                isChecked={checkedValue === 0}
                onChange={() => onChange(0)}
                label={<AccessIcon access="PUBLIC" />}
            />
            <Radio
                id="access-1"
                name="PROTECTED"
                isChecked={checkedValue === 1}
                onChange={() => onChange(1)}
                label={<AccessIcon access="PROTECTED" />}
            />
            <Radio
                id="access-2"
                name="PRIVATE"
                isChecked={checkedValue === 2}
                onChange={() => onChange(2)}
                label={<AccessIcon access="PRIVATE" />}
            />
        </>
    )
}
