import { Radio } from "@patternfly/react-core"
import AccessIcon from "./AccessIcon"
import { Access } from "../api"

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
                label={<AccessIcon access={0} />}
            />
            <Radio
                id="access-1"
                name="PROTECTED"
                isChecked={checkedValue === 1}
                onChange={() => onChange(1)}
                label={<AccessIcon access={1} />}
            />
            <Radio
                id="access-2"
                name="PRIVATE"
                isChecked={checkedValue === 2}
                onChange={() => onChange(2)}
                label={<AccessIcon access={2} />}
            />
        </>
    )
}
