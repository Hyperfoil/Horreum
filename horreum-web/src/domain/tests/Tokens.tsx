import { useContext, useEffect, useState } from "react"
import { useSelector } from "react-redux"

import {
    Checkbox,
    ClipboardCopy,
    FormGroup,
    TextInput,
} from "@patternfly/react-core"

import { TabFunctionsRef } from "../../components/SavedTabs"

import { useTester, defaultTeamSelector } from "../../auth"
import { noop } from "../../utils"
import { Test, revokeTestToken, addTestToken, TestToken } from "../../api"
import { AppContext } from "../../context/appContext";
import { AppContextType } from "../../context/@types/appContextTypes";
import SplitForm from "../../components/SplitForm"

const byteToHex: string[] = []

for (let n = 0; n <= 0xff; ++n) {
    const hexOctet = n.toString(16).padStart(2, "0")
    byteToHex.push(hexOctet)
}

function randomToken() {
    const u8 = new Uint8Array(32)
    window.crypto.getRandomValues(u8)
    const hexOctets = []
    for (let i = 0; i < u8.length; ++i) hexOctets.push(byteToHex[u8[i]])
    return hexOctets.join("")
}

type TestTokenExtended = TestToken & {
    // temporary
    name: string
    modified?: boolean
}

type TokensProps = {
    test?: Test
    onModified(modified: boolean): void
    funcsRef: TabFunctionsRef
}

const extendTokens = (tokens: TestToken[]): TestTokenExtended[] => {
    return tokens.map((t: TestToken, _) => {
        return {
            ...t,
            name: t.description,
            modified: false
        }
    })
}

function Tokens(props: TokensProps) {
    const { alerting } = useContext(AppContext) as AppContextType;
    const defaultRole = useSelector(defaultTeamSelector)
    const [owner] = useState(props.test?.owner || defaultRole || "")
    const isTester = useTester(owner)

    const [tokens, setTokens] = useState<TestTokenExtended[]>([])
    const [selectedToken, setSelectedToken] = useState<TestTokenExtended>()
    const [revokedTokens, setRevokedTokens] = useState<TestTokenExtended[]>([])
    
    useEffect(() => {
        if (!props.test) {
            return
        }

        const extendedTokens = extendTokens(props.test?.tokens ?? [])
        setTokens(extendedTokens)
        if (extendedTokens.length > 0) {
            setSelectedToken(extendedTokens[0])
        }
    }, [props.test, defaultRole])

    const updateSelectedToken = (patch: Partial<TestTokenExtended>) => {
        if (selectedToken) {
            const updatedToken = { ...selectedToken, ...patch, modified: true}
            setSelectedToken(updatedToken)

            // update local tokens
            const newTokens = tokens.filter(t => t.id !== selectedToken.id)
            newTokens.push(updatedToken)
            setTokens(newTokens)

            props.onModified(true)
        }
    }

    const isNewToken = (t: TestTokenExtended): boolean => {
        return t.id < 0
    }

    props.funcsRef.current = {
        save: () => {
            // check if there are modified tokens without permissions or description
            const tokensWithErrors = tokens.filter(t => t.modified && (t.permissions === 0 || t.description.trim() === ""))
            if (tokensWithErrors.length > 0) {
                return alerting.dispatchError("Missing required fields for test token(s)", "ADD_TEST_TOKEN", "Malformed test token(s)")
            }
            return Promise.all([
                ...revokedTokens
                    .map(toRevoke => {
                        if (toRevoke.testId) {
                            return revokeTestToken(toRevoke.testId, toRevoke.id, alerting)
                                .then(noop)
                        }
                    }),
                ...tokens
                    .filter(t => t.modified && t.permissions !== 0)
                    .map(newToken => {
                        if (newToken.id < 0 && newToken.testId) {
                            // we can only create new tokens
                            return addTestToken(newToken.testId, newToken.value, newToken.description, newToken.permissions, alerting)
                                .then(noop)
                        }
                    })
            ]).then(() => {
                setRevokedTokens([])
            })   
        }
        ,
        reset: () => {
            setTokens([])
            setSelectedToken(undefined)
            setRevokedTokens([])
        }
    }
    return (
        <SplitForm
            itemType="Token"
            newItem={id => ({
                id,
                name: "",
                description: "",
                permissions: 0,
                value: randomToken(),
                testId: props.test?.id,
                modified: true
            })}
            canAddItem={isTester}
            canDelete={isTester}
            addItemText="Add new token"
            noItemTitle="No tokens defined"
            noItemText="This test does not define any custom access token."
            items={tokens}
            onDelete={token => {
                if (token.id >= 0) {
                    setRevokedTokens([...revokedTokens, token])
                    props.onModified(true)
                }
            }}
            onChange={tokens => {
                setTokens(tokens)
                props.onModified(true)
            }}
            selected={selectedToken}
            onSelected={token => setSelectedToken(token)}
            loading={tokens === undefined}
            deleteButtonText="Revoke"
        >
            {selectedToken && (
                <>
                    <FormGroup label="Description" fieldId="description" isRequired={isNewToken(selectedToken)}>
                        <TextInput 
                            id="description"
                            isDisabled={!isNewToken(selectedToken)}
                            value={selectedToken.description}
                            validated={selectedToken.description.trim().length > 0 ? "default" : "error"}
                            onChange={(_event, val) => updateSelectedToken({description: val, name: val})}
                        />
                    </FormGroup>
                    {/* the token is showed only during the creation */}
                    {isNewToken(selectedToken) && (
                        <>
                            <FormGroup label="Token" fieldId="token">
                                <ClipboardCopy id="token" isReadOnly>
                                    {selectedToken.value}
                                </ClipboardCopy>
                            </FormGroup>
                            <FormGroup label="Link" fieldId="link">
                                <ClipboardCopy id="link" isReadOnly>
                                    {window.location.origin + "/test/" + selectedToken.testId + "?token=" + selectedToken.value}
                                </ClipboardCopy>
                            </FormGroup>
                        </>
                    )}
                    <FormGroup label="Permissions" fieldId="permissions" isRequired={isNewToken(selectedToken)}>
                        <Checkbox
                            id="read"
                            label="Read"
                            isDisabled={!isNewToken(selectedToken)}
                            isChecked={(selectedToken.permissions & 1) !== 0}
                            onChange={(_event, checked) => {
                                updateSelectedToken({ permissions: (selectedToken.permissions & ~5) | (checked ? 1 : 0) })
                            }}
                        />
                        <Checkbox
                            id="modify"
                            label="Modify"
                            isDisabled={!isNewToken(selectedToken)}
                            isChecked={(selectedToken.permissions & 2) !== 0}
                            onChange={(_event, checked) => {
                                updateSelectedToken({ permissions: (selectedToken.permissions & ~2) | (checked ? 2 : 0) })
                            }}
                        />
                        <Checkbox
                            id="upload"
                            label="Upload"
                            isDisabled={!isNewToken(selectedToken)}
                            isChecked={(selectedToken.permissions & 4) !== 0}
                            onChange={(_event, checked) => {
                                updateSelectedToken({ permissions: (selectedToken.permissions & ~4) | (checked ? 5 : 0) })
                            }}
                        />
                    </FormGroup>
                </>
            )}
        </SplitForm>
    )
}

export default Tokens
