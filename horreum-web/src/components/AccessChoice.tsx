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
                isChecked={checkedValue === Access.Public}
                onChange={() => onChange(Access.Public)}
                label={<AccessIcon access={Access.Public} />}
            />
            <Radio
                id="access-1"
                name="PROTECTED"
                isChecked={checkedValue === Access.Protected}
                onChange={() => onChange(Access.Protected)}
                label={<AccessIcon access={Access.Protected} />}
            />
            <Radio
                id="access-2"
                name="PRIVATE"
                isChecked={checkedValue === Access.Private}
                onChange={() => onChange(Access.Private)}
                label={<AccessIcon access={Access.Private} />}
            />
        </>
    )
}
