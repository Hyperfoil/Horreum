import { useEffect, useState } from "react"
import { useDispatch, useSelector } from "react-redux"

import {
    Button,
    ButtonVariant,
    Checkbox,
    ClipboardCopy,
    DataList,
    DataListAction,
    DataListCell,
    DataListItem,
    DataListItemRow,
    DataListItemCells,
    Form,
    FormGroup,
    Modal,
    TextInput,
} from "@patternfly/react-core"

import AccessChoice from "../../components/AccessChoice"
import AccessIcon from "../../components/AccessIcon"
import TeamSelect from "../../components/TeamSelect"
import { TabFunctionsRef } from "../../components/SavedTabs"

import { useTester, teamToName, Access as authAccess, defaultTeamSelector } from "../../auth"
import { noop } from "../../utils"
import { addToken, revokeToken, updateAccess } from "./actions"
import { TestDispatch } from "./reducers"
import { Test } from "../../api"

type AddTokenModalProps = {
    testId: number
    isOpen: boolean
    onClose(): void
    onSubmit(value: string, description: string, permissions: number): Promise<unknown>
}

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

function AddTokenModal(props: AddTokenModalProps) {
    const [isSaving, setSaving] = useState(false)
    const [value, setValue] = useState(randomToken())
    const [description, setDescription] = useState("")
    const [permissions, setPermissions] = useState(0)
    const onClose = () => {
        setSaving(false)
        setValue(randomToken())
        setDescription("")
        setPermissions(0)
        props.onClose()
    }
    return (
        <Modal
            title="Create a new token"
            isOpen={props.isOpen}
            onClose={onClose}
            actions={[
                <Button
                    key="save"
                    variant={ButtonVariant.primary}
                    isDisabled={isSaving || permissions === 0 || description === ""}
                    onClick={() => {
                        setSaving(true)
                        props.onSubmit(value, description, permissions).catch(noop).finally(onClose)
                    }}
                >
                    Save
                </Button>,
                <Button key="cancel" variant={ButtonVariant.link} isDisabled={isSaving} onClick={onClose}>
                    Cancel
                </Button>,
            ]}
        >
            <Form isHorizontal={true}>
                <FormGroup label="Description" fieldId="description">
                    <TextInput id="description" value={description} onChange={setDescription} />
                </FormGroup>
                <FormGroup label="Token" fieldId="token">
                    <ClipboardCopy id="token" isReadOnly>
                        {value}
                    </ClipboardCopy>
                </FormGroup>
                <FormGroup label="Link" fieldId="link">
                    <ClipboardCopy id="link" isReadOnly>
                        {window.location.origin + "/test/" + props.testId + "?token=" + value}
                    </ClipboardCopy>
                </FormGroup>
                <FormGroup label="Permissions" fieldId="">
                    <Checkbox
                        id="read"
                        label="Read"
                        isChecked={(permissions & 1) !== 0}
                        onChange={checked => {
                            setPermissions((permissions & ~5) | (checked ? 1 : 0))
                        }}
                    />
                    <Checkbox
                        id="modify"
                        label="Modify"
                        isChecked={(permissions & 2) !== 0}
                        onChange={checked => {
                            setPermissions((permissions & ~2) | (checked ? 2 : 0))
                        }}
                    />
                    <Checkbox
                        id="upload"
                        label="Upload"
                        isChecked={(permissions & 4) !== 0}
                        onChange={checked => {
                            setPermissions((permissions & ~4) | (checked ? 5 : 0))
                        }}
                    />
                </FormGroup>
            </Form>
        </Modal>
    )
}

type AccessProps = {
    test?: Test
    onModified(modified: boolean): void
    funcsRef: TabFunctionsRef
}

function Access(props: AccessProps) {
    const defaultRole = useSelector(defaultTeamSelector)
    const [access, setAccess] = useState<authAccess>(props.test?.access || 0)
    const [owner, setOwner] = useState(props.test?.owner || defaultRole || "")
    const [modalOpen, setModalOpen] = useState(false)
    const isTester = useTester(owner)

    useEffect(() => {
        setOwner(props.test?.owner || defaultRole || "")
        setAccess(props.test?.access || 0)
    }, [props.test, defaultRole])

    const dispatch = useDispatch<TestDispatch>()
    props.funcsRef.current = {
        save: () => {
            if (!props.test) {
                return Promise.reject()
            }
            return dispatch(updateAccess(props.test.id, owner, access))
        },
        reset: () => {
            setOwner(props.test?.owner || defaultRole || "")
            setAccess(props.test?.access || 0)
        },
    }

    return (
        <Form isHorizontal={true} style={{ gridGap: "2px", width: "100%", paddingRight: "8px" }}>
            <h2>Permissions</h2>
            <FormGroup label="Owner" fieldId="testOwner">
                {isTester ? (
                    <TeamSelect
                        includeGeneral={false}
                        selection={teamToName(owner) || ""}
                        onSelect={selection => {
                            setOwner(selection.key)
                            props.onModified(true)
                        }}
                    />
                ) : (
                    <TextInput value={teamToName(owner) || ""} id="testOwner" isReadOnly />
                )}
            </FormGroup>
            <FormGroup label="Access rights" fieldId="testAccess">
                {isTester ? (
                    <AccessChoice
                        checkedValue={access}
                        onChange={a => {
                            setAccess(a)
                            props.onModified(true)
                        }}
                    />
                ) : (
                    <AccessIcon access={access} />
                )}
            </FormGroup>
            <h2>Tokens</h2>
            {isTester && (
                <div>
                    <Button onClick={() => setModalOpen(true)}>Add new token</Button>
                </div>
            )}
            <DataList aria-label="List of tokens">
                <DataListItem aria-labelledby="simple-item1">
                    {(props.test?.tokens || []).map((token, i) => (
                        <DataListItemRow key={i}>
                            <DataListItemCells
                                dataListCells={[
                                    <DataListCell key="description">{token.description}</DataListCell>,
                                    <DataListCell key="permissions">
                                        <Checkbox id="read" label="Read" isChecked={(token.permissions & 1) !== 0} />
                                        <Checkbox id="modify" label="Modify" isChecked={(token.permissions & 2) !== 0} />
                                        <Checkbox id="upload" label="Upload" isChecked={(token.permissions & 4) !== 0} />
                                    </DataListCell>,
                                ]}
                            />
                            <DataListAction
                                id="revoke"
                                aria-labelledby="revoke"
                                aria-label="Revoke"
                                isPlainButtonAction
                            >
                                <Button
                                    onClick={() => {
                                        if (props.test?.id) {
                                            dispatch(revokeToken(props.test?.id, token.id)).catch(noop)
                                        }
                                    }}
                                >
                                    Revoke
                                </Button>
                            </DataListAction>
                        </DataListItemRow>
                    ))}
                </DataListItem>
            </DataList>
            <AddTokenModal
                testId={props.test?.id || 0}
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                onSubmit={(value, description, permissions) => {
                    if (!props.test) {
                        return Promise.reject()
                    }
                    return dispatch(addToken(props.test.id, value, description, permissions))
                }}
            />
        </Form>
    )
}

export default Access
