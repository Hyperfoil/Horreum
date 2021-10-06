import { ReactElement, ReactNode, useEffect, useMemo, useState, useRef } from "react"
import { useHistory } from "react-router"
import { Location, UnregisterCallback } from "history"
import { ActionGroup, Button, Spinner, Tab, Tabs } from "@patternfly/react-core"
import SaveChangesModal from "./SaveChangesModal"
import { noop } from "../utils"

type SavedTabProps = {
    title: string
    fragment: string
    onSave(): Promise<any>
    onReset(): void
    isModified(): boolean
    isHidden?: boolean
    children: ReactNode
}

export const SavedTab: React.FunctionComponent<SavedTabProps> = (_props: SavedTabProps) => null

type SavedTabsProps = {
    afterSave?(): Promise<any> | void
    afterReset?(): void
    children: ReactElement<SavedTabProps> | ReactElement<SavedTabProps>[]
    canSave?: boolean
}

export default function SavedTabs(props: SavedTabsProps) {
    const history = useHistory()
    const children = useMemo(
        () => (Array.isArray(props.children) ? props.children : [props.children]),
        [props.children]
    )
    const [activeKey, setActiveKey] = useState(() => {
        const index = children.findIndex(c => history.location.hash === "#" + c.props.fragment)
        return index < 0 ? 0 : index
    })
    const [requestedKey, setRequestedKey] = useState<number>()
    const [saving, setSaving] = useState(false)
    const goToTab = (index: number) => {
        setActiveKey(index)
        history.replace(history.location.pathname + "#" + children[index].props.fragment)
    }
    const [requestedLocation, setRequestedLocation] = useState<Location<any>>()
    const historyUnblock = useRef<UnregisterCallback>()
    useEffect(() => {
        const unblock = history.block(location => {
            if (children[activeKey].props.isModified()) {
                setRequestedLocation(location)
                return false
            }
        })
        historyUnblock.current = unblock
        return () => {
            unblock()
        }
    }, [activeKey, children, history])
    const navigate = () => {
        if (requestedKey !== undefined) {
            goToTab(requestedKey as number)
        }
        if (requestedLocation !== undefined) {
            console.log(historyUnblock)
            console.log(requestedLocation)
            if (historyUnblock.current) {
                historyUnblock.current()
            }
            history.push(requestedLocation)
        }
        setRequestedKey(undefined)
        setRequestedLocation(undefined)
    }
    return (
        <>
            <SaveChangesModal
                isOpen={requestedKey !== undefined || requestedLocation !== undefined}
                onClose={() => {
                    setRequestedKey(undefined)
                    setRequestedLocation(undefined)
                }}
                onSave={() =>
                    children[activeKey].props.onSave().then(_ => {
                        navigate()
                        if (props.afterSave) {
                            return props.afterSave()
                        }
                    })
                }
                onReset={() => {
                    children[activeKey].props.onReset()
                    if (props.afterReset) {
                        props.afterReset()
                    }
                    navigate()
                }}
            />
            <Tabs
                activeKey={activeKey}
                onSelect={(_, key) => {
                    if (children[activeKey].props.isModified()) {
                        setRequestedKey(key as number)
                    } else {
                        goToTab(key as number)
                    }
                }}
            >
                {children.map((c, i) => (
                    <Tab key={i} eventKey={i} title={c.props.title} isHidden={c.props.isHidden}>
                        {c.props.children}
                    </Tab>
                ))}
            </Tabs>
            {props.canSave !== false && (
                <ActionGroup style={{ marginTop: 0 }}>
                    <Button
                        variant="primary"
                        isDisabled={saving}
                        onClick={() => {
                            setSaving(true)
                            children[activeKey].props
                                .onSave()
                                .then(() => {
                                    if (props.afterSave) {
                                        return props.afterSave()
                                    }
                                }, noop)
                                .finally(() => {
                                    setSaving(false)
                                })
                        }}
                    >
                        {saving ? (
                            <>
                                {"Saving... "}
                                <Spinner size="md" />
                            </>
                        ) : (
                            "Save"
                        )}
                    </Button>
                </ActionGroup>
            )}
        </>
    )
}
