import {useContext, useEffect, useRef, useState} from "react"
import { Dropdown, DropdownItem, DropdownToggle, FormGroup, InputGroup, TextInput } from "@patternfly/react-core"
import { AllowedSite, getAllowedSites} from "../api"
import {AppContext} from "../context/appContext";
import {AppContextType} from "../context/@types/appContextTypes";

function isValidUrl(url: string) {
    try {
        new URL(url)
        return true
    } catch (_) {
        return false
    }
}

type HttpActionUrlSelectorProps = {
    active: boolean
    value: string
    setValue(value: string): void
    isDisabled?: boolean
    isReadOnly?: boolean
    setValid?(valid: boolean): void
    extraCheck?(value: string): string | boolean
}

export default function HttpActionUrlSelector({ active, value, setValue, isDisabled, isReadOnly, setValid, extraCheck}: HttpActionUrlSelectorProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const [prefixes, setPrefixes] = useState<AllowedSite[]>([{ id: -1, prefix: "" }])
    useEffect(() => {
        if (active) {
            getAllowedSites(alerting).then(setPrefixes)
        }
    }, [active])

    const isUrlValid = isValidUrl(value)
    const isUrlAllowed = prefixes.some(p => value.startsWith(p.prefix))
    const extraCheckResult = extraCheck ? extraCheck(value) : true

    const [dropdownOpen, setDropdownOpen] = useState(false)

    const ref = useRef<any>()

    return (
        <FormGroup
            label="HTTP Action URL"
            validated={isUrlValid && isUrlAllowed && extraCheckResult === true ? "default" : "error"}
            isRequired={true}
            fieldId="url"
            helperText="URL (with protocol) for POST callback"
            helperTextInvalid={
                !isUrlValid
                    ? "URL cannot be parsed."
                    : !isUrlAllowed
                    ? "URL does not start with any of the allowed prefixes."
                    : extraCheckResult
            }
        >
            <InputGroup>
                {!isReadOnly && (
                    <Dropdown
                        onSelect={event => {
                            if (event && event.currentTarget) {
                                if (setValid) {
                                    setValid(true)
                                }
                                setValue(event.currentTarget.innerText)
                            }
                            setDropdownOpen(false)
                            if (ref.current) {
                                ref.current.focus()
                            }
                        }}
                        toggle={
                            <DropdownToggle onToggle={setDropdownOpen} isDisabled={isDisabled}>
                                Pick URL prefix
                            </DropdownToggle>
                        }
                        isOpen={dropdownOpen}
                        dropdownItems={prefixes.map((p, i) => (
                            <DropdownItem key={i} value={p.prefix} component="button">
                                {p.prefix}
                            </DropdownItem>
                        ))}
                    />
                )}
                <TextInput
                    ref={ref}
                    value={value}
                    isRequired
                    type="text"
                    id="url"
                    aria-describedby="url-helper"
                    name="url"
                    validated={isUrlValid && isUrlAllowed && extraCheckResult === true ? "default" : "error"}
                    placeholder="e.g. 'http://example.com/api/action'"
                    onChange={value => {
                        value = value.trim()
                        if (setValid) {
                            setValid(
                                isValidUrl(value) &&
                                    prefixes.some(p => value.startsWith(p.prefix)) &&
                                    (!extraCheck || extraCheck(value) === true)
                            )
                        }
                        setValue(value)
                    }}
                    isDisabled={isDisabled}
                    isReadOnly={isReadOnly}
                />
            </InputGroup>
        </FormGroup>
    )
}
