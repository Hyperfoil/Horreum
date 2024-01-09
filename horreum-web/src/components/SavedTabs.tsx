import { MutableRefObject, ReactElement, useEffect, useMemo, useState, useRef } from "react"
import { useHistory } from "react-router"
import { Location, UnregisterCallback } from "history"
import { ActionGroup, Button, Spinner } from "@patternfly/react-core"
import SaveChangesModal from "./SaveChangesModal"
import FragmentTabs, { FragmentTab, FragmentTabProps } from "./FragmentTabs"
import { noop } from "../utils"

export type TabFunctions = {
    save(): Promise<any>
    reset(): void
    modified?(): boolean
}

export type TabFunctionsRef = MutableRefObject<TabFunctions | undefined>

export function saveFunc(ref: TabFunctionsRef) {
    return () => (ref.current ? ref.current.save() : Promise.resolve())
}
export function resetFunc(ref: TabFunctionsRef) {
    return () => ref.current?.reset()
}
export function modifiedFunc(ref: TabFunctionsRef) {
    return () => ref.current?.modified?.() || false
}

type SavedTabProps = FragmentTabProps & {
    onSave(): Promise<any>
    onReset?(): void
    isModified(): boolean
}

export const SavedTab: React.FunctionComponent<SavedTabProps> = (_props: SavedTabProps) => null

type SavedTabsProps = {
    afterSave?(): Promise<any> | void
    afterReset?(): void
    children: ReactElement<SavedTabProps | FragmentTabProps> | ReactElement<SavedTabProps | FragmentTabProps>[]
    canSave?: boolean
}

export default function SavedTabs(props: SavedTabsProps) {
    const history = useHistory()
    const children = useMemo(
        () => (Array.isArray(props.children) ? props.children : [props.children]),
        [props.children]
    )
    const activeKey = useRef(0)
    const [requestedNavigation, setRequestedNavigation] = useState<() => void>()
    const [saving, setSaving] = useState(false)
    const [requestedLocation, setRequestedLocation] = useState<Location<any>>()
    const historyUnblock = useRef<UnregisterCallback>()
    useEffect(() => {
        const unblock = history.block(location => {
            const childProps = children[activeKey.current].props
            if ("isModified" in childProps && childProps.isModified()) {
                setRequestedLocation(location)
                return false
            }
        })
        historyUnblock.current = unblock
        return () => {
            unblock()
        }
    }, [activeKey.current, children, history])
    const navigate = () => {
        if (requestedNavigation !== undefined) {
            requestedNavigation()
        }
        if (requestedLocation !== undefined) {
            if (historyUnblock.current) {
                historyUnblock.current()
            }
            history.push(requestedLocation)
        }
        setRequestedNavigation(undefined)
        setRequestedLocation(undefined)
    }
    return (
        <>
            <SaveChangesModal
                isOpen={requestedNavigation !== undefined || requestedLocation !== undefined}
                onClose={() => {
                    setRequestedNavigation(undefined)
                    setRequestedLocation(undefined)
                }}
                onSave={() => {
                    const childProps = children[activeKey.current].props
                    if ("onSave" in childProps) {
                        return childProps.onSave().then(_ => {
                            navigate()
                            if (props.afterSave) {
                                return props.afterSave()
                            }
                        })
                    } else {
                        return Promise.reject("Cannot save unsaveable tab!")
                    }
                }}
                onReset={() => {
                    const childProps = children[activeKey.current].props
                    if ("onReset" in childProps) {
                        if( childProps.onReset ) {
                            childProps.onReset()
                        }
                        if (props.afterReset) {
                            props.afterReset()
                        }
                    }
                    navigate()
                }}
            />
            <FragmentTabs
                tabIndexRef={activeKey}
                navigate={_ => {
                    const childProps = children[activeKey.current].props
                    if ("isModified" in childProps && childProps.isModified()) {
                        return new Promise((resolve, _) => {
                            setRequestedNavigation(() => resolve)
                        })
                    } else {
                        return Promise.resolve()
                    }
                }}
            >
                {children.map((c, i) => (
                    <FragmentTab key={i} {...c.props} />
                ))}
            </FragmentTabs>
            {props.canSave !== false && "onSave" in children[activeKey.current].props && (
                <ActionGroup style={{ marginTop: 0 }}>
                    <Button
                        variant="primary"
                        isDisabled={saving}
                        onClick={() => {
                            setSaving(true)
                            const childProps = children[activeKey.current].props
                            if ("onSave" in childProps) {
                                childProps
                                    .onSave()
                                    .then(() => {
                                        if (props.afterSave) {
                                            return props.afterSave()
                                        }
                                    }, noop)
                                    .finally(() => {
                                        setSaving(false)
                                    })
                            }
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
