import {useContext, useEffect, useRef, useState} from "react"
import {
	FormGroup,
	InputGroup,
	TextInput, InputGroupItem,
    HelperText,
    HelperTextItem,
    FormHelperText,

} from '@patternfly/react-core';
import {
	Dropdown,
	DropdownItem,
	DropdownToggle
} from '@patternfly/react-core/deprecated';

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
            isRequired={true}
            fieldId="url"
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
                            <DropdownToggle onToggle={(_event, val) => setDropdownOpen(val)} isDisabled={isDisabled}>
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
                <InputGroupItem isFill ><TextInput
                    ref={ref}
                    value={value}
                    isRequired
                    type="text"
                    id="url"
                    aria-describedby="url-helper"
                    name="url"
                    validated={isUrlValid && isUrlAllowed && extraCheckResult === true ? "default" : "error"}
                    placeholder="e.g. 'http://example.com/api/action'"
                    onChange={(_event, value) => {
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
                     readOnlyVariant={isReadOnly ? "default" : undefined}
                /></InputGroupItem>
            </InputGroup>
            <FormHelperText>
                <HelperText>
                    <HelperTextItem variant={isUrlValid && isUrlAllowed && extraCheckResult === true ? "default" : "error"}>
                    {isUrlValid && isUrlAllowed && extraCheckResult === true ? "URL (with protocol) for POST callback" : !isUrlValid
                        ? "URL cannot be parsed."
                        : !isUrlAllowed
                        ? "URL does not start with any of the allowed prefixes."
                        : extraCheckResult ? "URL (with protocol) for POST callback" : ""}
                    </HelperTextItem>
                </HelperText>
            </FormHelperText>
        </FormGroup>
    )
}
